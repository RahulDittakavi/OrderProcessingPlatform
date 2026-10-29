package main

import (
	"context"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestActuatorHealthCheck(t *testing.T) {
	tests := []struct {
		name      string
		status    int
		body      string
		wantErr   bool
		errorText string
	}{
		{name: "healthy", status: http.StatusOK, body: `{"status":"UP"}`},
		{name: "down", status: http.StatusServiceUnavailable, body: `{"status":"DOWN"}`, wantErr: true, errorText: "HTTP 503"},
		{name: "unhealthy body", status: http.StatusOK, body: `{"status":"DOWN"}`, wantErr: true, errorText: `health status is "DOWN"`},
		{name: "invalid body", status: http.StatusOK, body: "not json", wantErr: true, errorText: "decode health response"},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, _ *http.Request) {
				writer.WriteHeader(test.status)
				_, _ = writer.Write([]byte(test.body))
			}))
			defer server.Close()

			check := httpHealthCheck("test", server.URL, true)
			err := check.run(context.Background(), server.Client())
			if (err != nil) != test.wantErr {
				t.Fatalf("run() error = %v, wantErr %v", err, test.wantErr)
			}
			if err != nil && !strings.Contains(err.Error(), test.errorText) {
				t.Fatalf("run() error = %q, want it to contain %q", err, test.errorText)
			}
		})
	}
}

func TestTCPHealthCheck(t *testing.T) {
	listener, err := net.Listen("tcp", "localhost:0")
	if err != nil {
		t.Fatal(err)
	}
	defer listener.Close()

	check := tcpHealthCheck("test", listener.Addr().String())
	if err := check.run(context.Background(), nil); err != nil {
		t.Fatalf("run() error = %v, want nil", err)
	}
}

func TestRunChecksPreservesOrderAndTimesOut(t *testing.T) {
	checks := []healthCheck{
		{name: "slow", endpoint: "slow", run: func(ctx context.Context, _ *http.Client) error {
			<-ctx.Done()
			return ctx.Err()
		}},
		{name: "fast", endpoint: "fast", run: func(context.Context, *http.Client) error { return nil }},
	}

	results := runChecks(context.Background(), http.DefaultClient, checks, 10*time.Millisecond)
	if len(results) != 2 || results[0].name != "slow" || results[1].name != "fast" {
		t.Fatalf("runChecks() results = %#v, want input order", results)
	}
	if results[0].err == nil {
		t.Fatal("runChecks() slow check error = nil, want timeout")
	}
	if results[1].err != nil {
		t.Fatalf("runChecks() fast check error = %v, want nil", results[1].err)
	}
}

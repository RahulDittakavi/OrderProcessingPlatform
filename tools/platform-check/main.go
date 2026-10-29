package main

import (
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"net"
	"net/http"
	"os"
	"sync"
	"time"
)

type healthCheck struct {
	name     string
	endpoint string
	run      func(context.Context, *http.Client) error
}

type checkResult struct {
	name     string
	endpoint string
	duration time.Duration
	err      error
}

func main() {
	timeout := flag.Duration("timeout", 3*time.Second, "maximum time allowed for each health check")
	flag.Parse()

	if *timeout <= 0 {
		fmt.Fprintln(os.Stderr, "timeout must be greater than zero")
		os.Exit(2)
	}

	client := &http.Client{Timeout: *timeout}
	results := runChecks(context.Background(), client, defaultChecks(), *timeout)

	failures := 0
	for _, result := range results {
		status := "UP"
		if result.err != nil {
			status = "DOWN"
			failures++
		}

		fmt.Printf("%-22s %-4s %-38s %s", result.name, status, result.endpoint, result.duration.Round(time.Millisecond))
		if result.err != nil {
			fmt.Printf(" - %v", result.err)
		}
		fmt.Println()
	}

	fmt.Printf("\n%d/%d checks passed\n", len(results)-failures, len(results))
	if failures > 0 {
		os.Exit(1)
	}
}

func defaultChecks() []healthCheck {
	return []healthCheck{
		httpHealthCheck("API Gateway", "http://localhost:9000/actuator/health", true),
		httpHealthCheck("Discovery Server", "http://localhost:8761/actuator/health", true),
		httpHealthCheck("Product Service", "http://localhost:8080/actuator/health", true),
		httpHealthCheck("Order Service", "http://localhost:8081/actuator/health", true),
		httpHealthCheck("Inventory Service", "http://localhost:8082/actuator/health", true),
		tcpHealthCheck("PostgreSQL", "localhost:5432"),
		tcpHealthCheck("Kafka", "localhost:9092"),
		httpHealthCheck("Prometheus", "http://localhost:9090/-/ready", false),
		httpHealthCheck("Grafana", "http://localhost:3000/api/health", false),
	}
}

func runChecks(ctx context.Context, client *http.Client, checks []healthCheck, timeout time.Duration) []checkResult {
	results := make([]checkResult, len(checks))
	var waitGroup sync.WaitGroup

	for index, check := range checks {
		waitGroup.Add(1)
		go func(index int, check healthCheck) {
			defer waitGroup.Done()

			checkCtx, cancel := context.WithTimeout(ctx, timeout)
			defer cancel()

			start := time.Now()
			err := check.run(checkCtx, client)
			results[index] = checkResult{
				name:     check.name,
				endpoint: check.endpoint,
				duration: time.Since(start),
				err:      err,
			}
		}(index, check)
	}

	waitGroup.Wait()
	return results
}

func httpHealthCheck(name, endpoint string, requireActuatorUp bool) healthCheck {
	return healthCheck{
		name:     name,
		endpoint: endpoint,
		run: func(ctx context.Context, client *http.Client) error {
			request, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
			if err != nil {
				return fmt.Errorf("create request: %w", err)
			}

			response, err := client.Do(request)
			if err != nil {
				return err
			}
			defer response.Body.Close()

			if response.StatusCode < http.StatusOK || response.StatusCode >= http.StatusMultipleChoices {
				return fmt.Errorf("HTTP %s", response.Status)
			}

			if requireActuatorUp {
				var body struct {
					Status string `json:"status"`
				}
				if err := json.NewDecoder(response.Body).Decode(&body); err != nil {
					return fmt.Errorf("decode health response: %w", err)
				}
				if body.Status != "UP" {
					return fmt.Errorf("health status is %q", body.Status)
				}
			}
			return nil
		},
	}
}

func tcpHealthCheck(name, endpoint string) healthCheck {
	return healthCheck{
		name:     name,
		endpoint: endpoint,
		run: func(ctx context.Context, _ *http.Client) error {
			connection, err := (&net.Dialer{}).DialContext(ctx, "tcp", endpoint)
			if err != nil {
				return err
			}
			if err := connection.Close(); err != nil {
				return fmt.Errorf("close connection: %w", err)
			}
			return nil
		},
	}
}

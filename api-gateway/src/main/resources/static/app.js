const productBody = document.querySelector("#products-body");
const orderBody = document.querySelector("#orders-body");
const orderSelect = document.querySelector("#order-product");
const orderForm = document.querySelector("#order-form");
const productForm = document.querySelector("#product-form");
const productDialog = document.querySelector("#product-dialog");
const toast = document.querySelector("#toast");

let products = [];
let orders = [];
let toastTimeout;
let pendingOrderSubmission;

const currency = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" });

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (character) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;",
  })[character]);
}

async function requestJson(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: { ...(options.body ? { "Content-Type": "application/json" } : {}), ...options.headers },
  });

  if (!response.ok) {
    const detail = await response.text();
    let message = detail;
    try {
      const parsed = JSON.parse(detail);
      message = parsed.message || parsed.error || detail;
    } catch {
      // Preserve non-JSON service error messages.
    }
    throw new Error(message || `Request failed (${response.status})`);
  }

  if (response.status === 204) return null;
  return response.json();
}

function showToast(message, isError = false) {
  clearTimeout(toastTimeout);
  toast.textContent = message;
  toast.classList.toggle("error", isError);
  toast.classList.add("visible");
  toastTimeout = setTimeout(() => toast.classList.remove("visible"), 3600);
}

function setConnection(connected) {
  const state = document.querySelector("#connection-state");
  state.classList.toggle("connected", connected);
  state.classList.toggle("disconnected", !connected);
  state.lastElementChild.textContent = connected ? "Gateway connected" : "Gateway unavailable";
}

function productName(productId) {
  const product = products.find((item) => item.id === productId);
  return product ? product.name : "Deleted product";
}

function renderProducts(inventoryBySku) {
  document.querySelector("#product-count").textContent = products.length;
  document.querySelector("#catalog-count").textContent = `${products.length} ${products.length === 1 ? "ITEM" : "ITEMS"}`;
  orderSelect.innerHTML = '<option value="">Select a product</option>' + products.map((product) =>
    `<option value="${escapeHtml(product.id)}">${escapeHtml(product.name)} · ${currency.format(product.price)}</option>`,
  ).join("");

  if (products.length === 0) {
    productBody.innerHTML = '<tr><td class="table-message" colspan="4">No products yet. Add one to start taking orders.</td></tr>';
    return;
  }

  productBody.innerHTML = products.map((product) => {
    const inventory = inventoryBySku[product.skuCode];
    const quantity = inventory?.availableQuantity;
    const stockLabel = Number.isFinite(quantity) ? `${quantity} units` : "Not seeded";
    const stockClass = Number.isFinite(quantity) ? (quantity > 5 ? "in-stock" : "low-stock") : "";
    return `<tr>
      <td><span class="product-name">${escapeHtml(product.name)}</span><span class="product-description">${escapeHtml(product.description)}</span></td>
      <td><span class="sku-text">${escapeHtml(product.skuCode)}</span></td>
      <td>${currency.format(product.price)}</td>
      <td><span class="stock-cell"><span class="stock-dot ${stockClass}"></span>${stockLabel}</span></td>
    </tr>`;
  }).join("");
}

function renderOrders() {
  document.querySelector("#order-count").textContent = orders.length;
  document.querySelector("#confirmed-count").textContent = orders.filter((order) => order.status === "CONFIRMED").length;
  document.querySelector("#orders-updated").textContent = `UPDATED ${new Date().toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })}`;

  if (orders.length === 0) {
    orderBody.innerHTML = '<tr><td class="table-message" colspan="5">No orders to show yet.</td></tr>';
    return;
  }

  orderBody.innerHTML = orders.slice().reverse().slice(0, 8).map((order) => {
    const status = String(order.status ?? "UNKNOWN").toLowerCase();
    return `<tr>
      <td><span class="order-reference">${escapeHtml(order.orderNumber || order.id || "—")}</span></td>
      <td>${escapeHtml(productName(order.productId))}</td>
      <td>${escapeHtml(order.quantity)}</td>
      <td>${currency.format(order.price)}</td>
      <td><span class="status-pill status-${escapeHtml(status)}">${escapeHtml(order.status || "Unknown")}</span></td>
    </tr>`;
  }).join("");
}

async function loadDashboard() {
  const refreshButton = document.querySelector("#refresh-button");
  refreshButton.disabled = true;
  try {
    const [nextProducts, nextOrders] = await Promise.all([
      requestJson("/api/products"),
      requestJson("/api/orders"),
    ]);
    products = nextProducts;
    orders = nextOrders;
    const inventoryEntries = await Promise.all(products.map(async (product) => {
      try {
        return [product.skuCode, await requestJson(`/api/inventory/${encodeURIComponent(product.skuCode)}`)];
      } catch {
        return [product.skuCode, null];
      }
    }));
    renderProducts(Object.fromEntries(inventoryEntries));
    renderOrders();
    setConnection(true);
  } catch (error) {
    setConnection(false);
    productBody.innerHTML = `<tr><td class="table-message" colspan="4">${escapeHtml(error.message || "Could not load platform data.")}</td></tr>`;
    orderBody.innerHTML = '<tr><td class="table-message" colspan="5">Orders are unavailable until the gateway reconnects.</td></tr>';
    showToast("Could not load platform data. Check that the services are running.", true);
  } finally {
    refreshButton.disabled = false;
  }
}

function updateOrderTotal() {
  const product = products.find((item) => item.id === orderSelect.value);
  const quantity = Number(document.querySelector("#order-quantity").value) || 0;
  document.querySelector("#order-total").textContent = currency.format(product ? product.price * quantity : 0);
}

orderSelect.addEventListener("change", updateOrderTotal);
document.querySelector("#order-quantity").addEventListener("input", updateOrderTotal);
document.querySelector("#refresh-button").addEventListener("click", loadDashboard);
document.querySelector("#open-product-dialog").addEventListener("click", () => productDialog.showModal());
document.querySelector("#close-product-dialog").addEventListener("click", () => productDialog.close());
document.querySelector("#cancel-product-dialog").addEventListener("click", () => productDialog.close());

orderForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const button = document.querySelector("#place-order-button");
  const feedback = document.querySelector("#order-feedback");
  const formData = new FormData(orderForm);
  const orderRequest = {
    productId: formData.get("productId"),
    quantity: Number(formData.get("quantity")),
  };
  const requestFingerprint = JSON.stringify(orderRequest);
  if (!pendingOrderSubmission || pendingOrderSubmission.fingerprint !== requestFingerprint) {
    pendingOrderSubmission = {
      fingerprint: requestFingerprint,
      key: crypto.randomUUID(),
    };
  }
  button.disabled = true;
  feedback.textContent = "";
  try {
    await requestJson("/api/orders", {
      method: "POST",
      headers: { "Idempotency-Key": pendingOrderSubmission.key },
      body: JSON.stringify(orderRequest),
    });
    pendingOrderSubmission = undefined;
    orderForm.reset();
    updateOrderTotal();
    await loadDashboard();
    showToast("Order placed successfully.");
  } catch (error) {
    feedback.textContent = error.message || "The order could not be placed.";
  } finally {
    button.disabled = false;
  }
});

productForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const button = document.querySelector("#save-product-button");
  const feedback = document.querySelector("#product-feedback");
  const formData = new FormData(productForm);
  button.disabled = true;
  feedback.textContent = "";
  try {
    await requestJson("/api/products", {
      method: "POST",
      body: JSON.stringify({
        skuCode: formData.get("skuCode").trim(),
        name: formData.get("name").trim(),
        description: formData.get("description").trim(),
        price: Number(formData.get("price")),
      }),
    });
    productForm.reset();
    productDialog.close();
    await loadDashboard();
    showToast("Product added to the catalog.");
  } catch (error) {
    feedback.textContent = error.message || "The product could not be saved.";
  } finally {
    button.disabled = false;
  }
});

document.querySelector("#today-label").textContent = new Intl.DateTimeFormat("en-US", {
  weekday: "short", month: "short", day: "numeric",
}).format(new Date()).toUpperCase();

loadDashboard();
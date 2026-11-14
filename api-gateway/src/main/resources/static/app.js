const productBody = document.querySelector("#products-body");
const orderBody = document.querySelector("#orders-body");
const orderSelect = document.querySelector("#order-product");
const orderForm = document.querySelector("#order-form");
const productForm = document.querySelector("#product-form");
const productDialog = document.querySelector("#product-dialog");
const stockDialog = document.querySelector("#stock-dialog");
const stockForm = document.querySelector("#stock-form");
const toast = document.querySelector("#toast");

let products = [];
let orders = [];
let inventoryBySku = {};
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
    `<option value="${escapeHtml(product.id)}"${(inventoryBySku[product.skuCode]?.availableQuantity ?? 0) <= 0 ? " disabled" : ""}>${escapeHtml(product.name)} · ${currency.format(product.price)}${(inventoryBySku[product.skuCode]?.availableQuantity ?? 0) <= 0 ? " · out of stock" : ""}</option>`,
  ).join("");

  if (products.length === 0) {
    productBody.innerHTML = '<tr><td class="table-message" colspan="5">No products yet. Add one to start taking orders.</td></tr>';
    updateOrderAvailability();
    return;
  }

  productBody.innerHTML = products.map((product) => {
    const inventory = inventoryBySku[product.skuCode];
    const quantity = inventory?.availableQuantity;
    const stockLabel = !Number.isFinite(quantity)
      ? "Unavailable"
      : quantity === 0
        ? "Out of stock"
        : quantity <= 5
          ? `Low · ${quantity}`
          : `In stock · ${quantity}`;
    const stockClass = !Number.isFinite(quantity)
      ? "stock-unknown"
      : quantity === 0
        ? "out-of-stock"
        : quantity <= 5
          ? "low-stock"
          : "in-stock";
    return `<tr>
      <td><span class="product-name">${escapeHtml(product.name)}</span><span class="product-description">${escapeHtml(product.description)}</span></td>
      <td><span class="sku-text">${escapeHtml(product.skuCode)}</span></td>
      <td>${currency.format(product.price)}</td>
      <td><span class="stock-badge ${stockClass}">${stockLabel}</span></td>
      <td><button class="stock-action" type="button" data-restock-sku="${escapeHtml(product.skuCode)}" data-restock-name="${escapeHtml(product.name)}">Add stock</button></td>
    </tr>`;
  }).join("");
  updateOrderAvailability();
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
    inventoryBySku = Object.fromEntries(inventoryEntries);
    renderProducts(inventoryBySku);
    renderOrders();
    updateOrderTotal();
    setConnection(true);
  } catch (error) {
    setConnection(false);
    inventoryBySku = {};
    updateOrderAvailability();
    productBody.innerHTML = `<tr><td class="table-message" colspan="5">${escapeHtml(error.message || "Could not load platform data.")}</td></tr>`;
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
  updateOrderAvailability(product, quantity);
}

function updateOrderAvailability(
  product = products.find((item) => item.id === orderSelect.value),
  quantity = Number(document.querySelector("#order-quantity").value) || 0,
) {
  const available = product ? inventoryBySku[product.skuCode]?.availableQuantity : null;
  const note = document.querySelector("#order-stock-note");
  const button = document.querySelector("#place-order-button");

  if (!product) {
    note.textContent = "Choose an in-stock product to continue.";
  } else if (!Number.isFinite(available)) {
    note.textContent = "Stock information is unavailable. Refresh and try again.";
  } else if (available === 0) {
    note.textContent = "This product is out of stock. Add inventory from the catalog.";
  } else if (quantity > available) {
    note.textContent = `Only ${available} ${available === 1 ? "unit is" : "units are"} available.`;
  } else {
    note.textContent = `${available} ${available === 1 ? "unit" : "units"} available.`;
  }

  button.disabled = !product || !Number.isFinite(available) || available < 1 || quantity < 1 || quantity > available;
}

orderSelect.addEventListener("change", updateOrderTotal);
document.querySelector("#order-quantity").addEventListener("input", updateOrderTotal);
document.querySelector("#refresh-button").addEventListener("click", loadDashboard);
document.querySelector("#open-product-dialog").addEventListener("click", () => productDialog.showModal());
document.querySelector("#close-product-dialog").addEventListener("click", () => productDialog.close());
document.querySelector("#cancel-product-dialog").addEventListener("click", () => productDialog.close());
document.querySelector("#close-stock-dialog").addEventListener("click", () => stockDialog.close());
document.querySelector("#cancel-stock-dialog").addEventListener("click", () => stockDialog.close());

productBody.addEventListener("click", (event) => {
  const button = event.target.closest("[data-restock-sku]");
  if (!button) return;

  document.querySelector("#stock-sku").value = button.dataset.restockSku;
  document.querySelector("#stock-product-name").textContent =
    `${button.dataset.restockName} · ${button.dataset.restockSku}`;
  document.querySelector("#stock-feedback").textContent = "";
  stockDialog.showModal();
  document.querySelector("#stock-quantity").focus();
});

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

stockForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const button = document.querySelector("#save-stock-button");
  const feedback = document.querySelector("#stock-feedback");
  const skuCode = document.querySelector("#stock-sku").value;
  const quantity = Number(new FormData(stockForm).get("quantity"));
  button.disabled = true;
  feedback.textContent = "";

  try {
    await requestJson(`/api/inventory/${encodeURIComponent(skuCode)}/stock`, {
      method: "POST",
      body: JSON.stringify({ quantity }),
    });
    stockDialog.close();
    stockForm.reset();
    await loadDashboard();
    showToast(`${quantity} ${quantity === 1 ? "unit" : "units"} added to inventory.`);
  } catch (error) {
    feedback.textContent = error.message || "Stock could not be added.";
  } finally {
    button.disabled = false;
  }
});

document.querySelector("#today-label").textContent = new Intl.DateTimeFormat("en-US", {
  weekday: "short", month: "short", day: "numeric",
}).format(new Date()).toUpperCase();

loadDashboard();
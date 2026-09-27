let allProducts = [];


/* =========================
   LOAD PRODUCTS
========================= */

async function loadProducts() {

    const container =
        document.getElementById("productsContainer");

    try {

        const response = await apiRequest("/products");

        // Spring Boot returns a Page object.
        // The actual products are inside response.content
        allProducts = response.content || [];

        displayProducts(allProducts);

    } catch (error) {

        console.error("Product loading error:", error);

        container.innerHTML = `
            <p class="loading">
                Unable to load products.
            </p>
        `;
    }
}


/* =========================
   DISPLAY PRODUCTS
========================= */

function displayProducts(products) {

    const container =
        document.getElementById("productsContainer");

    if (!products || products.length === 0) {

        container.innerHTML = `
            <p class="loading">
                No products found.
            </p>
        `;

        return;
    }

    container.innerHTML = products.map(product => {

        const image = product.imageUrl
            ? `<img src="${product.imageUrl}" alt="${product.name}">`
            : `<div class="no-image">🛍️</div>`;

        return `
            <div class="product-card">

                <div class="product-image">
                    ${image}
                </div>

                <div class="product-info">

                    <div class="product-category">
                        ${product.category || "Product"}
                    </div>

                    <h3 class="product-name">
                        ${product.name}
                    </h3>

                    <p class="product-description">
                        ${product.description || "Quality product"}
                    </p>

                    <div class="product-bottom">

                        <span class="product-price">
                            ₹${product.price}
                        </span>

                        <button
                            class="add-cart-btn"
                            onclick="addToCart(${product.id})"
                        >
                            Add to Cart
                        </button>

                    </div>

                </div>

            </div>
        `;

    }).join("");
}


/* =========================
   SEARCH PRODUCTS
========================= */

function searchProducts() {

    const search =
        document
            .getElementById("searchInput")
            .value
            .toLowerCase()
            .trim();

    if (!search) {

        displayProducts(allProducts);

        return;
    }

    const filtered =
        allProducts.filter(product =>
            product.name
                .toLowerCase()
                .includes(search)
        );

    displayProducts(filtered);
}


/* =========================
   ADD TO CART
========================= */

async function addToCart(productId) {

    const token =
        localStorage.getItem("accessToken");

    if (!token) {

        alert("Please login first.");

        window.location.href = "login.html";

        return;
    }

    try {

        await apiRequest("/cart/items", {

            method: "POST",

            body: JSON.stringify({

                productId: productId,

                quantity: 1

            })

        });

        alert("Product added to cart!");

    } catch (error) {

        console.error(error);

        alert(error.message);

    }
}


/* =========================
   START
========================= */

document.addEventListener(
    "DOMContentLoaded",
    loadProducts
);
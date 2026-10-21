(function () {
    function money(value) {
        return new Intl.NumberFormat("en-IN", {
            style: "currency", currency: "INR", maximumFractionDigits: 2
        }).format(Number(value));
    }

    function setConnection(text, connected) {
        var element = document.getElementById("live-connection");
        if (!element) return;
        element.textContent = text;
        element.style.color = connected ? "#0a5c38" : "#7b1a1a";
        element.style.background = connected ? "#d1f0e3" : "#fde8e8";
    }

    function closeLiveView(message) {
        var status = document.getElementById("live-status");
        if (status) {
            status.textContent = "CLOSED";
            status.className = "badge-closed";
        }
        document.querySelectorAll("[data-live-form]").forEach(function (form) {
            var box = form.closest(".bid-box, .proposal-box");
            if (box) box.remove();
        });
        var countdown = document.getElementById("countdown") || document.getElementById("daysLeft");
        if (countdown) countdown.textContent = "Closed";
        if (message.winner) {
            var winner = document.getElementById("live-winner");
            if (winner) {
                winner.hidden = false;
                winner.querySelector("[data-winner-name]").textContent = message.winner;
            }
        }
    }

    function renderBid(config, message) {
        if (document.querySelector('[data-bid-id="' + message.bidId + '"]')) return;
        var body = document.getElementById("live-bid-body");
        if (!body) return;
        var empty = body.querySelector("[data-empty-row]");
        if (empty) empty.remove();

        var row = document.createElement("tr");
        row.dataset.bidId = message.bidId;
        row.dataset.amount = message.amount;
        if (config.type === "auction") {
            row.innerHTML = "<td data-rank></td><td></td><td></td><td></td>";
            row.children[1].textContent = message.bidderName;
            row.children[2].textContent = money(message.amount);
            row.children[3].textContent = message.time;
        } else {
            row.innerHTML = "<td data-rank></td><td></td><td></td>";
            row.children[1].textContent = message.bidderName;
            row.children[2].textContent = money(message.amount);
        }
        body.appendChild(row);

        var rows = Array.from(body.querySelectorAll("tr[data-bid-id]"));
        rows.sort(function (a, b) {
            var delta = Number(a.dataset.amount) - Number(b.dataset.amount);
            return config.type === "auction" ? -delta : delta;
        });
        rows.forEach(function (item, index) {
            item.querySelector("[data-rank]").textContent = String(index + 1);
            body.appendChild(item);
        });

        var count = document.getElementById("live-total");
        if (count) count.textContent = message.totalParticipants;
        if (config.type === "auction") {
            var highest = document.getElementById("live-highest");
            if (highest) highest.textContent = money(rows[0].dataset.amount);
            var minimum = document.querySelector("[data-live-minimum]");
            if (minimum) minimum.textContent = money(rows[0].dataset.amount);
            var input = document.querySelector('[data-live-form] input[name="bidAmount"]');
            if (input) input.min = String(Number(rows[0].dataset.amount) + 0.01);
        }
        body.parentElement.scrollIntoView({ behavior: "smooth", block: "nearest" });
    }

    window.startLiveBidding = function (config) {
        var socket = new SockJS(config.endpoint || "/ws");
        var client = Stomp.over(socket);
        client.debug = function () {};
        client.connect({}, function () {
            setConnection("Live connected", true);
            client.subscribe(config.topic, function (frame) {
                var message = JSON.parse(frame.body);
                if (message.event === "CLOSED") closeLiveView(message);
                else renderBid(config, message);
                document.querySelectorAll("[data-live-form] button[type='submit']")
                    .forEach(function (button) { button.disabled = false; });
            });
        }, function () {
            setConnection("Reconnecting...", false);
            window.setTimeout(function () { window.startLiveBidding(config); }, 3000);
        });

        document.querySelectorAll("[data-live-form]").forEach(function (form) {
            form.addEventListener("submit", function (event) {
                event.preventDefault();
                var button = form.querySelector("button[type='submit']");
                if (button.disabled) return;
                button.disabled = true;
                fetch(form.action, {
                    method: "POST", body: new FormData(form),
                    credentials: "same-origin", redirect: "follow"
                }).catch(function () {
                    button.disabled = false;
                    setConnection("Submission failed", false);
                });
                window.setTimeout(function () { button.disabled = false; }, 5000);
            });
        });
    };
})();

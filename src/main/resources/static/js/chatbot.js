(function () {
    'use strict';

    if (document.getElementById('chatbot-widget')) return;

    const widgetHTML = `
    <button id="chatbot-toggle" type="button" title="Chat with us" aria-label="Open chatbot">
        💬
        <span class="badge-dot"></span>
    </button>
    <div id="chatbot-panel" role="dialog" aria-label="SmartPortal Assistant">
        <div id="chatbot-header">
            <div class="avatar">🤖</div>
            <div class="header-info">
                <h4>SmartPortal Assistant</h4>
                <span id="chatbot-status">Online - Ask me anything</span>
            </div>
            <button class="close-btn" id="chatbot-close" type="button" title="Close chat" aria-label="Close chatbot">×</button>
        </div>
        <div id="chatbot-messages" aria-live="polite"></div>
        <div id="chatbot-input-area">
            <input type="text" id="chatbot-input" placeholder="Type your question..." autocomplete="off" />
            <button id="chatbot-send" type="button" title="Send" aria-label="Send message">➤</button>
        </div>
    </div>`;

    const container = document.createElement('div');
    container.id = 'chatbot-widget';
    container.innerHTML = widgetHTML;
    document.body.appendChild(container);

    const toggleBtn = document.getElementById('chatbot-toggle');
    const panel = document.getElementById('chatbot-panel');
    const closeBtn = document.getElementById('chatbot-close');
    const messagesEl = document.getElementById('chatbot-messages');
    const inputEl = document.getElementById('chatbot-input');
    const sendBtn = document.getElementById('chatbot-send');
    const statusEl = document.getElementById('chatbot-status');

    let isOpen = false;
    let isWaiting = false;
    let csrfPromise = null;

    toggleBtn.addEventListener('click', () => {
        if (isOpen) {
            closePanel();
            return;
        }
        isOpen = true;
        panel.classList.add('open');
        toggleBtn.textContent = '×';
        toggleBtn.setAttribute('aria-label', 'Close chatbot');
        inputEl.focus();
        if (messagesEl.children.length === 0) showWelcome();
    });

    closeBtn.addEventListener('click', closePanel);
    sendBtn.addEventListener('click', sendMessage);
    inputEl.addEventListener('keydown', (event) => {
        if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault();
            sendMessage();
        }
    });

    function closePanel() {
        isOpen = false;
        panel.classList.remove('open');
        toggleBtn.innerHTML = '💬<span class="badge-dot"></span>';
        toggleBtn.setAttribute('aria-label', 'Open chatbot');
    }

    async function sendMessage() {
        const text = inputEl.value.trim();
        if (!text || isWaiting) return;

        addTextMessage(text, 'user');
        inputEl.value = '';
        setWaiting(true);
        showTyping();

        try {
            const csrf = await getCsrfToken();
            const response = await fetch('/api/chatbot', {
                method: 'POST',
                credentials: 'same-origin',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json',
                    [csrf.headerName]: csrf.token
                },
                body: JSON.stringify({ message: text })
            });

            const data = await readJson(response);
            if (!response.ok) {
                throw new Error(data && data.reply ? data.reply : 'Chat request failed');
            }

            removeTyping();
            addBotMessage(data.reply || 'I could not find an answer for that yet.');
            if (Array.isArray(data.suggestions) && data.suggestions.length > 0) {
                addSuggestions(data.suggestions);
            }
            statusEl.textContent = 'Online - Ask me anything';
        } catch (error) {
            removeTyping();
            addTextMessage(
                    error.message === 'Forbidden'
                            ? 'Your session could not be verified. Refresh the page and try again.'
                            : 'Sorry, I could not send that message. Please try again.',
                    'bot error');
            statusEl.textContent = 'Connection issue';
        } finally {
            setWaiting(false);
            inputEl.focus();
        }
    }

    async function getCsrfToken() {
        const existing = document.querySelector('input[name="_csrf"]');
        if (existing && existing.value) {
            return { headerName: 'X-CSRF-TOKEN', token: existing.value };
        }

        if (!csrfPromise) {
            csrfPromise = fetch('/api/chatbot/csrf', {
                method: 'GET',
                credentials: 'same-origin',
                headers: { 'Accept': 'application/json' }
            }).then(async (response) => {
                const data = await readJson(response);
                if (!response.ok || !data || !data.token || !data.headerName) {
                    throw new Error(response.status === 403 ? 'Forbidden' : 'CSRF token unavailable');
                }
                return data;
            }).catch((error) => {
                csrfPromise = null;
                throw error;
            });
        }
        return csrfPromise;
    }

    async function readJson(response) {
        const text = await response.text();
        if (!text) return {};
        try {
            return JSON.parse(text);
        } catch {
            return { reply: text };
        }
    }

    function setWaiting(waiting) {
        isWaiting = waiting;
        sendBtn.disabled = waiting;
        inputEl.disabled = waiting;
        panel.setAttribute('aria-busy', waiting ? 'true' : 'false');
    }

    function addTextMessage(content, sender) {
        const div = document.createElement('div');
        div.className = 'chat-msg ' + sender;
        div.textContent = content;
        messagesEl.appendChild(div);
        scrollToBottom();
    }

    function addBotMessage(content) {
        const div = document.createElement('div');
        div.className = 'chat-msg bot';
        div.innerHTML = formatMarkdown(content);
        messagesEl.appendChild(div);
        scrollToBottom();
    }

    function addSuggestions(suggestions) {
        const wrapper = document.createElement('div');
        wrapper.className = 'chat-suggestions';
        suggestions.forEach((text) => {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.textContent = text;
            btn.addEventListener('click', () => {
                wrapper.remove();
                inputEl.value = text;
                sendMessage();
            });
            wrapper.appendChild(btn);
        });
        messagesEl.appendChild(wrapper);
        scrollToBottom();
    }

    function showTyping() {
        removeTyping();
        const div = document.createElement('div');
        div.className = 'typing-indicator';
        div.id = 'chatbot-typing';
        div.innerHTML = '<span></span><span></span><span></span>';
        messagesEl.appendChild(div);
        scrollToBottom();
    }

    function removeTyping() {
        const el = document.getElementById('chatbot-typing');
        if (el) el.remove();
    }

    function showWelcome() {
        addBotMessage(
                "Hello! I'm the **SmartPortal Assistant**.\n\n" +
                "I can help you with:\n" +
                "- Auctions and bidding\n" +
                "- Tenders and proposals\n" +
                "- Account and login\n" +
                "- Platform navigation\n\n" +
                "What would you like to know?");
        addSuggestions([
            'How do I sign up?',
            'How to bid on an auction?',
            'How to fill a tender?',
            'What are the user roles?'
        ]);
    }

    function formatMarkdown(text) {
        return escapeHtml(String(text))
                .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
                .replace(/_(.*?)_/g, '<em>$1</em>')
                .replace(/\n/g, '<br>');
    }

    function escapeHtml(text) {
        return text
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;')
                .replace(/'/g, '&#039;');
    }

    function scrollToBottom() {
        requestAnimationFrame(() => {
            messagesEl.scrollTop = messagesEl.scrollHeight;
        });
    }
})();

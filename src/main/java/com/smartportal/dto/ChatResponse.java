package com.smartportal.dto;

import java.util.List;

public class ChatResponse {
    private String reply;
    private List<String> suggestions;

    public ChatResponse() {}

    public ChatResponse(String reply, List<String> suggestions) {
        this.reply = reply;
        this.suggestions = suggestions;
    }

    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }

    public List<String> getSuggestions() { return suggestions; }
    public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }
}

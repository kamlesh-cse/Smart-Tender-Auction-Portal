package com.smartportal.dto;

public class BidMessage {

    private String event;
    private String bidId;
    private String amount;
    private String bidderName;
    private String time;
    private String rank;
    private String totalParticipants;
    private String auctionStatus;
    private String winner;

    public static BidMessage closed(String winner) {
        BidMessage message = new BidMessage();
        message.setEvent("CLOSED");
        message.setWinner(winner);
        return message;
    }

    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }
    public String getBidId() { return bidId; }
    public void setBidId(String bidId) { this.bidId = bidId; }
    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }
    public String getBidderName() { return bidderName; }
    public void setBidderName(String bidderName) { this.bidderName = bidderName; }
    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
    public String getRank() { return rank; }
    public void setRank(String rank) { this.rank = rank; }
    public String getTotalParticipants() { return totalParticipants; }
    public void setTotalParticipants(String totalParticipants) { this.totalParticipants = totalParticipants; }
    public String getAuctionStatus() { return auctionStatus; }
    public void setAuctionStatus(String auctionStatus) { this.auctionStatus = auctionStatus; }
    public String getWinner() { return winner; }
    public void setWinner(String winner) { this.winner = winner; }
}

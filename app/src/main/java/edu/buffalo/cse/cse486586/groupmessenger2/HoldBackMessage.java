package edu.buffalo.cse.cse486586.groupmessenger2;

/**
 * This class represents the message that is present in HoldBackQueue
 * Each message has a message ID, message string, sequence number and a deliverable status
 *
 * Created by swati on 3/6/18.
 */

public class HoldBackMessage {

    private String msgId;
    private String msgString;
    private double seqNo;

    private boolean deliverable;

    public HoldBackMessage(String msgId, String msgString, double seqNo, boolean deliverable){
        this.msgId = msgId;
        this.msgString = msgString;
        this.seqNo = seqNo;
        this.deliverable = deliverable;
    }

    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    public String getMsgString() {
        return msgString;
    }

    public void setMsgString(String msgString) {
        this.msgString = msgString;
    }

    public double getSeqNo() {
        return seqNo;
    }

    public void setSeqNo(double seqNo) {
        this.seqNo = seqNo;
    }

    public boolean isDeliverable() {
        return deliverable;
    }

    public void setDeliverable(boolean canBeDelivered) {
        this.deliverable = canBeDelivered;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HoldBackMessage that = (HoldBackMessage) o;

        if (msgId != null ? !msgId.equals(that.msgId) : that.msgId != null) return false;
        return msgString != null ? msgString.equals(that.msgString) : that.msgString == null;
    }

    @Override
    public int hashCode() {
        int result = msgId != null ? msgId.hashCode() : 0;
        result = 31 * result + (msgString != null ? msgString.hashCode() : 0);
        return result;
    }
}

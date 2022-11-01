package paxos;
import java.io.Serializable;

/**
 * Please fill in the data structure you use to represent the request message for each RMI call.
 * Hint: You may need the sequence number for each paxos instance and also you may need proposal number and value.
 * Hint: Make it more generic such that you can use it for each RMI call.
 * Hint: Easier to make each variable public
 */
public class Request implements Serializable {
    static final long serialVersionUID=1L;
    // Your data here
    public int seq; // sequence number of instance sending the request
    public int n; //proposal number 
    public Object v; // value of the proposal
    public int done;  // the proposal was accepted after a sequence number
    public int me;  //index of instance sending the request

    // Your constructor and methods here
    public Request() {
    	this.seq = -1;
    	this.n = -1;
    	this.v = null;
    	this.done = -1;
    }
    public Request(int seq, int proposalNum, Object v, int done, int me) {
    	this.seq = seq;
    	this.n = proposalNum;
    	this.v = v;
    	this.done = done;
    	this.me = me;
    }
}

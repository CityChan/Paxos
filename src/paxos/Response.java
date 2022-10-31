package paxos;
import java.io.Serializable;

/**
 * Please fill in the data structure you use to represent the response message for each RMI call.
 * Hint: You may need a boolean variable to indicate ack of acceptors and also you may need proposal number and value.
 * Hint: Make it more generic such that you can use it for each RMI call.
 */
public class Response implements Serializable {
    static final long serialVersionUID=2L;
    // your data here
    public int seq;
    public Object v;
    public int Np;
    public boolean AcceptedProposal = false;
    public boolean AcceptedAccept = false;
    public boolean Majority = false;


    // Your constructor and methods here
    public Response() {
    	this.seq = -1;
    	this.v = null;
    	this.Np = Integer.MIN_VALUE;
    }
    
    public Response(int seq, Object v, int Np) {
    	this.seq = seq;
    	this.v = v;
    	this.Np = Np;
    }
}

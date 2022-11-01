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
    public int n;
    public int n_a;
    public Object v_a;


    // Your constructor and methods here
    public Response() {
    	this.n = -1;;
    	this.n_a = -1;
    	this.v_a = null;
    }
    
    public Response(int n, int n_a, Object v_a) {
    	this.n = n;
    	this.n_a = n_a;
    	this.v_a = v_a;
    }
}

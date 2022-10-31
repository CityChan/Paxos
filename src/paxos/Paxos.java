package paxos;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.registry.Registry;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class is the main class you need to implement paxos instances.
 * @param <Instance>
 */
public class Paxos implements PaxosRMI, Runnable{
	

    
    ReentrantLock mutex;
    String[] peers; // hostname
    int[] ports; // host port
    int me; // index into peers[]

    Registry registry;
    PaxosRMI stub;

    AtomicBoolean dead;// for testing
    AtomicBoolean unreliable;// for testing

    // Your data here
    int seq;
    Map<Integer, Instance> instances;
    int[] dones;
    Object value;
    int majority;

    //define Instance for paxos
    private class Instance{
    	int LargestNp;
    	int LargestNa;
    	State state;
    	Object value;
    	public Instance() {
    		this.LargestNp = Integer.MIN_VALUE;
    		this.LargestNa = Integer.MIN_VALUE;
    		this.state = State.Pending;
    		this.value = null;
    	}
    }
    
    //get Instance with a given seqence number
    private Instance getInstance(int seq) {
    	mutex.lock();
    	if(!instances.containsKey(seq)) {
    		Instance instance = new Instance();
    		instances.put(seq, instance);
    	}
    	mutex.unlock();
    	return instances.get(seq);
    	
    }
    
    /**
     * Call the constructor to create a Paxos peer.
     * The hostnames of all the Paxos peers (including this one)
     * are in peers[]. The ports are in ports[].
     */
    public Paxos(int me, String[] peers, int[] ports){

        this.me = me;
        this.peers = peers;
        this.ports = ports;
        this.mutex = new ReentrantLock();
        this.dead = new AtomicBoolean(false);
        this.unreliable = new AtomicBoolean(false);

        // Your initialization code here
        int seq = -1;
        this.instances = new ConcurrentHashMap<Integer, Instance>();
        Arrays.fill(this.dones, -1);
        this.value = null;
        this.majority = this.peers.length/2 + 1;
        // register peers, do not modify this part
        try{
            System.setProperty("java.rmi.server.hostname", this.peers[this.me]);
            registry = LocateRegistry.createRegistry(this.ports[this.me]);
            stub = (PaxosRMI) UnicastRemoteObject.exportObject(this, this.ports[this.me]);
            registry.rebind("Paxos", stub);
        } catch(Exception e){
            e.printStackTrace();
        }
    }


    /**
     * Call() sends an RMI to the RMI handler on server with
     * arguments rmi name, request message, and server id. It
     * waits for the reply and return a response message if
     * the server responded, and return null if Call() was not
     * be able to contact the server.
     *
     * You should assume that Call() will time out and return
     * null after a while if it doesn't get a reply from the server.
     *
     * Please use Call() to send all RMIs and please don't change
     * this function.
     */
    public Response Call(String rmi, Request req, int id){
        Response callReply = null;

        PaxosRMI stub;
        try{
            Registry registry=LocateRegistry.getRegistry(this.ports[id]);
            stub=(PaxosRMI) registry.lookup("Paxos");
            if(rmi.equals("Prepare"))
                callReply = stub.Prepare(req);
            else if(rmi.equals("Accept"))
                callReply = stub.Accept(req);
            else if(rmi.equals("Decide"))
                callReply = stub.Decide(req);
            else
                System.out.println("Wrong parameters!");
        } catch(Exception e){
            return null;
        }
        return callReply;
    }


    /**
     * The application wants Paxos to start agreement on instance seq,
     * with proposed value v. Start() should start a new thread to run
     * Paxos on instance seq. Multiple instances can be run concurrently.
     *
     * Hint: You may start a thread using the runnable interface of
     * Paxos object. One Paxos object may have multiple instances, each
     * instance corresponds to one proposed value/command. Java does not
     * support passing arguments to a thread, so you may reset seq and v
     * in Paxos object before starting a new thread. There is one issue
     * that variable may change before the new thread actually reads it.
     * Test won't fail in this case.
     *
     * Start() just starts a new thread to initialize the agreement.
     * The application will call Status() to find out if/when agreement
     * is reached.
     */
    public void Start(int seq, Object value){
        // Your code here
    	this.seq = seq;
    	this.value = value;
    	
    	Thread t = new Thread(this);
    	t.start();
    }

    @Override
    public void run(){
        //Your code here
    	if(this.seq < this.Min()) return;
    	while(this.getInstance(this.seq).state != State.Decided) {
    		// proposer is trying to propose a value and get majority accept
    		Response proposalResponse =  sendProposal(this.seq, this.value);
    		
    		if (proposalResponse.Majority == true) {
    			// if majority of accepters have accepted the proposed value, the proposer then sends accept request
    			Request acceptRequest = new Request(this.seq, proposalResponse.Np, proposalResponse.v);
    			boolean  accept_ok =  sendAccept(acceptRequest);
    		}
    	}
    }
    
    public Response sendProposal(int seq, Object v) {
    	Instance instance = this.getInstance(seq);
    	Object value = v;
    	//we need to generate an increasing proposal number
    	int Np;
    	int LargestNa = Integer.MIN_VALUE;;
    	//if instance was just created, the Proposal number must be MIN_VALUE
    	if(instance.LargestNp == Integer.MIN_VALUE) Np = this.me + 1;
    	else {
    		// if the Number of proposal is not equal to Min value, make sure it is increasing
    		Np = instance.LargestNp + this.peers.length + this.me + 1;
    	}
    	Request prepareRequest = new Request(seq, Np, value);
    	int countAccepted = 0;
    	
    	for(int i = 0; i < this.peers.length; i++) {
    		Response prepareResponse;
    		if(this.me == i) prepareResponse = this.Prepare(prepareRequest);
    		else {
    			prepareResponse = this.Call("Prepare", prepareRequest, i);
    		}
    		if(prepareResponse!= null && prepareResponse.AcceptedProposal == true) {
    			countAccepted++;
    			if(prepareResponse.Np > LargestNa) {
    				LargestNa = prepareResponse.Np;
    				value = prepareResponse.v;
    			}
    		}
    	}
    	Response proposalResponse = new Response();
    	
    	if(countAccepted >= this.majority) {
         	proposalResponse.Majority = true;
         	proposalResponse.Np = Np;			
         	proposalResponse.v = value;	
        }
        return proposalResponse;
    }

    // RMI handler
    public Response Prepare(Request req){
        // your code here

    }

    public Response Accept(Request req){
        // your code here

    }

    public Response Decide(Request req){
        // your code here

    }

    /**
     * The application on this machine is done with
     * all instances <= seq.
     *
     * see the comments for Min() for more explanation.
     */
    public void Done(int seq) {
        // Your code here
    }


    /**
     * The application wants to know the
     * highest instance sequence known to
     * this peer.
     */
    public int Max(){
        // Your code here
    }

    /**
     * Min() should return one more than the minimum among z_i,
     * where z_i is the highest number ever passed
     * to Done() on peer i. A peers z_i is -1 if it has
     * never called Done().

     * Paxos is required to have forgotten all information
     * about any instances it knows that are < Min().
     * The point is to free up memory in long-running
     * Paxos-based servers.

     * Paxos peers need to exchange their highest Done()
     * arguments in order to implement Min(). These
     * exchanges can be piggybacked on ordinary Paxos
     * agreement protocol messages, so it is OK if one
     * peers Min does not reflect another Peers Done()
     * until after the next instance is agreed to.

     * The fact that Min() is defined as a minimum over
     * all Paxos peers means that Min() cannot increase until
     * all peers have been heard from. So if a peer is dead
     * or unreachable, other peers Min()s will not increase
     * even if all reachable peers call Done. The reason for
     * this is that when the unreachable peer comes back to
     * life, it will need to catch up on instances that it
     * missed -- the other peers therefore cannot forget these
     * instances.
     */
    public int Min(){
        // Your code here

    }



    /**
     * the application wants to know whether this
     * peer thinks an instance has been decided,
     * and if so what the agreed value is. Status()
     * should just inspect the local peer state;
     * it should not contact other Paxos peers.
     */
    public retStatus Status(int seq){
        // Your code here

    }

    /**
     * helper class for Status() return
     */
    public class retStatus{
        public State state;
        public Object v;

        public retStatus(State state, Object v){
            this.state = state;
            this.v = v;
        }
    }

    /**
     * Tell the peer to shut itself down.
     * For testing.
     * Please don't change these four functions.
     */
    public void Kill(){
        this.dead.getAndSet(true);
        if(this.registry != null){
            try {
                UnicastRemoteObject.unexportObject(this.registry, true);
            } catch(Exception e){
                System.out.println("None reference");
            }
        }
    }

    public boolean isDead(){
        return this.dead.get();
    }

    public void setUnreliable(){
        this.unreliable.getAndSet(true);
    }

    public boolean isunreliable(){
        return this.unreliable.get();
    }


}

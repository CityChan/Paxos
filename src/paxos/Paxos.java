package paxos;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.registry.Registry;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class is the main class you need to implement paxos instances.
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
    public class Instance{
    	public int n_promised;
    	public int n_accepted;
    	public Object v_accepted;
    	public State state;
    	public Instance() {
    		this.n_promised = -1;
    		this.n_accepted = -1;
    		this.v_accepted = null;
    		this.state = State.Pending;
    	}
    }
    //record instances with different sequence numbers
    HashMap<Integer, Instance> instances;
    //peers complete jobs on one sequence
    int[] dones_on;
    //number of peers
    int peers_num;
    // number of majority
    int majority;
    //current sequence number
    int seq;
    //if the current instance has done
    int done_on;
    //value proposed by current instance
    Object v_proposed;

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
        this.instances = new HashMap<>();
        this.peers_num = peers.length;
        this.majority = peers_num/2 + 1;
        this.dones_on = new int[peers_num];
        for(int i = 0; i < peers_num; i++) dones_on[i] = -1;
        this.seq = -1;
        this.done_on = -1;
        this.v_proposed = null;

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
    	this.mutex.lock();
    	try {
    		if(seq < this.Min()) {
        		return;
        	}
    		this.seq = seq;
    		this.v_proposed = value;
    	}finally {
    		this.mutex.unlock();
    	}
    	Thread t = new Thread(this);
    	t.start();
    }

    @Override
    public void run(){
        //Your code here
    	int max_n_seen = -1;
    	int n_proposed;
    	Instance instance = instances.get(this.seq);
    	while (instance != null && instance.state == State.Decided) {
    		n_proposed = max_n_seen + 1;
    		max_n_seen++;
    		
    		//Propose Stage
    		Response[] responses = new Response[this.peers_num];
    		//prepare does not need to give value
    		Request prepareRequest = new Request(this.seq, n_proposed, null, this.done_on, this.me);
    		 for (int i = 0; i < peers_num; i++) {
    			 if(this.me == i) responses[i] = this.Prepare(prepareRequest);
    			 else {
    				 responses[i] = this.Call("Prepare",prepareRequest,i);
    			 }
    		 }
    		 int max_n_a = -1;
    		 int count_promises = 0;
    		 Object v_a = null;
    		 Object v_prime = null;
    		 for (Response response : responses) {
    			 if(response!= null) {
    				 max_n_seen = Math.max(max_n_seen, response.n_a);
    				 // if the proposal was promised
    				 if(response.n == n_proposed) {
    					 count_promises++;
    					 //update the max n_a seen and 
    					 if(response.n_a > max_n_a) {
    						 max_n_a = response.n_a;
    						 v_a = response.v_a;
    					 }
    				 }
    			 }
    		 }
    		 if(count_promises < this.majority) continue;
    		 
    		 if(v_a != null) v_prime = v_a;
			 else {
				 v_prime = this.v_proposed;
			 }
    		 
    		 
    		 //Accept Stage
    		 Request acceptRequest = new Request(this.seq, n_proposed, v_prime, this.done_on, this.me);
    		 for (int i = 0; i < peers_num; i++) {
    			 if(this.me == i) responses[i] = this.Accept(acceptRequest);
    			 else {
    				 responses[i] = this.Call("Accpet",acceptRequest,i);
    			 }
    		 }
    		 int count_accepts = 0;
    		 for (Response response : responses) {
    			 if(response!= null) {
    				 // if the proposal was accepted
    				 if(response.n == n_proposed) {
    					 count_accepts++;    					 
    				 }
    			 }
    		 }
    		 if(count_accepts < this.majority) continue;
    		 
    		 
    		 //Decide Stage
    		 Request decideRequest = new Request(this.seq, n_proposed, v_prime,  this.done_on, this.me);
    		 for (int i = 0; i < peers_num; i++) {
    			 if(this.me == i) responses[i] = this.Decide(decideRequest);
    			 else {
    				 responses[i] = this.Call("Decide",decideRequest,i);
    			 }
    		 }
    	}
    }

    // RMI handler
    public Response Prepare(Request req){
        // your code here
    	this.mutex.lock();
    	try {
    		if(!instances.containsKey(req.seq)) instances.put(req.seq, new Instance());
    		Instance instance = instances.get(req.seq);
    		//synchronize this.Dones[] from the request
    		this.dones_on[req.me] = req.done_on;
    		if(req.n > instance.n_promised) {
    			instance.n_promised = req.n;
    			return new Response(req.n, instance.n_accepted,instance.v_accepted);
    		}
    		else {
    			return new Response(-1, -1,null);
    		}
    	}finally {
    		this.mutex.unlock();
    	}
    }

    public Response Accept(Request req){
        // your code here
    	this.mutex.lock();
    	try {
    		if(!instances.containsKey(req.seq)) instances.put(req.seq, new Instance());
    		Instance instance = instances.get(req.seq);
    		//synchronize this.Dones[] from the request
    		this.dones_on[req.me] = req.done_on;
    		if(req.n >= instance.n_promised) {
    			instance.n_promised = req.n;
    			instance.n_accepted = req.n;
    			instance.v_accepted = req.v;
    			return new Response(req.n, instance.n_accepted,instance.v_accepted);
    		}
    		else {
    			return new Response(-1, -1, null);
    		}
    	}finally {
    		this.mutex.unlock();
    	}
    }

    public Response Decide(Request req){
        // your code here
    	this.mutex.lock();
    	try {
    		if(!instances.containsKey(req.seq)) instances.put(req.seq, new Instance());
    		Instance instance = instances.get(req.seq);
    		this.dones_on[req.me] = req.done_on;
    		instance.state = State.Decided;
    		instance.v_accepted = req.v;
    	}finally {
    		this.mutex.unlock();
    	}
		return new Response(req.n, instance.n_accepted, instance.v_accepted);

    }

    /**
     * The application on this machine is done with
     * all instances <= seq.
     *
     * see the comments for Min() for more explanation.
     */
    public void Done(int seq) {
        // Your code here
    	this.mutex.lock();
         try {
             this.done_on = Math.max(this.done_on, this.seq);
         }finally {
        	 this.mutex.unlock();
         }
    }


    /**
     * The application wants to know the
     * highest instance sequence known to
     * this peer.
     */
    public int Max(){
        // Your code here
    	int max = Integer.MIN_VALUE;
    	 this.mutex.lock();
         try {
        	 for(Integer key : this.instances.keySet()) {
        		 max = key > max? key : max;
        	 }
         }finally {
        	 this.mutex.unlock();
         }
         return max;
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
    	int min = Integer.MAX_VALUE;
    	this.mutex.lock();
    	try {
    		for(Integer done_on : this.dones_on) {
    			min = done_on < min? done_on : min;
    		}
    		
    		//for long-term running, release memeory
    		for(Integer key : this.instances.keySet()) {
    			if(key <= min) this.instances.get(key).state = State.Forgotten;
    		}
    	}finally {
    		this.mutex.unlock();
    	}
    	//following the instruction
    	return min + 1;
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
    	this.mutex.lock();
    	try {
    		Instance instance = this.instances.get(seq);
    		if(instance == null) return new retStatus(State.Pending, null);
    		else {
    			return new retStatus(instance.state, instance.v_accepted);
    		}
    	}finally {
    		this.mutex.unlock();
    	}
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

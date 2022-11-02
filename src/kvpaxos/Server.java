package kvpaxos;
import paxos.Paxos;
import paxos.State;
// You are allowed to call Paxos.Status to check if agreement was made.

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

public class Server implements KVPaxosRMI {

    ReentrantLock mutex;
    Registry registry;
    Paxos px;
    int me;

    String[] servers;
    int[] ports;
    KVPaxosRMI stub;

    // Your definitions here
    Map<String, Integer> kvMap;
    Set<Integer> Requests_History;
    int current_ClientSeq;

    public Server(String[] servers, int[] ports, int me){
        this.me = me;
        this.servers = servers;
        this.ports = ports;
        this.mutex = new ReentrantLock();
        this.px = new Paxos(me, servers, ports);
        // Your initialization code here
        this.kvMap = new HashMap<>();
        this.current_ClientSeq = 0;
        this.Requests_History = new HashSet<>();


        try{
            System.setProperty("java.rmi.server.hostname", this.servers[this.me]);
            registry = LocateRegistry.getRegistry(this.ports[this.me]);
            stub = (KVPaxosRMI) UnicastRemoteObject.exportObject(this, this.ports[this.me]);
            registry.rebind("KVPaxos", stub);
        } catch(Exception e){
            e.printStackTrace();
        }
    }
    //following instruction of the hw6
    public Op wait(int seq){
        int to = 10;
        while(true){
            Paxos.retStatus ret = this.px.Status(seq);
            if(ret.state == State.Decided){
                return Op.class.cast(ret.v);
            }
            try{
                Thread.sleep(to);
            } catch (Exception e){
                e.printStackTrace();
            }
            if( to < 1000){
                to = to * 2;
            }
        }
    }
    public void update_kvMap(int seq_Max) {
    	for(int i = current_ClientSeq; i <= seq_Max; i++) {
    		if(px.Status(i).state == State.Decided) {
    			Op operation = (Op)this.px.Status(i).v;
    			//if the operation got consensus and is put opertation
    			if(operation.op.equals("Put")) {
    				kvMap.put(operation.key, operation.value);
    			}
    			Requests_History.add(operation.ClientSeq);
    		}
    	}
    }
    // RMI handlers
    public Response Get(Request req){
        // Your code here
    	this.mutex.lock();
    	try {
    		int ClientSeq = req.operation.ClientSeq;
    		String key = req.operation.key;
    		
    		int seq_Max = this.px.Max();
    		for(int i = seq_Max+1; i < ClientSeq; i++) {
    			//fill the gap between seq_max in px and ClientSeq
    			//for the update kvMap method easier to implement
    			px.Start(i, null);
    			wait(i);
    		}
    		//get the latest seq_max
    		seq_Max = px.Max();
    		//make sure the get operation can obtain the latest value key pair
    		update_kvMap(seq_Max);
    		if(Requests_History.contains(ClientSeq)) {
    			// if there is duplicate, no need to start paxos again
    			Integer value = kvMap.getOrDefault(key,null);
    			return new Response(key, value, true);
    		}
    		//if client seq is not in the history
    		Requests_History.add(ClientSeq);
    		px.Start(ClientSeq, req.operation);
    		// wait for consensus
    		wait(ClientSeq);
    		
    		// done with consensus on sequence less than seq_Max
    		px.Done(seq_Max);
    		
    		//move the cursor to the next start point: seqMax + 1
    		current_ClientSeq = seq_Max + 1;
    		
    		Integer value = this.kvMap.getOrDefault(key,null);
			return new Response(key, value, true);
    	}finally {
    		this.mutex.unlock();
    	}
    }

    public Response Put(Request req){
        // Your code here
    	this.mutex.lock();
    	try {
    		int ClientSeq = req.operation.ClientSeq;
    		
    		int seq_Max = this.px.Max();
    		
    		for(int i = seq_Max+1; i < ClientSeq; i++) {
    			//fill the gap between seq_max in px and ClientSeq
    			//for the update kvMap method easier to implement
    			px.Start(i, null);
    			wait(i);
    		}
    		//get the latest seq_max
    		seq_Max = this.px.Max();
    		//make sure the get operation can obtain the latest value key pair
    		update_kvMap(seq_Max);
    		
    		if(Requests_History.contains(ClientSeq)) {
    			// if the op was in the history, the put operation was done
    			//no need to start paxos again
    			return new Response( req.operation.key,  req.operation.value, true);
    		}
    		
    		Requests_History.add(ClientSeq);
    		px.Start(ClientSeq, req.operation);
    		// wait for consensus
    		wait(ClientSeq);
    		
    		// done with consensus on sequence less than seq_Max
    		px.Done(seq_Max);
    		
    		//move the cursor to the next start point: seqMax + 1
    		current_ClientSeq = seq_Max + 1;
    		
    		//the put operation was not done, need once more update_kvMap()
    		return new Response( req.operation.key,  req.operation.value, false);
    		
    	}finally{
    		this.mutex.unlock();
    	}
    }


}

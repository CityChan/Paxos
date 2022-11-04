# Fault-tolerant key-value store based on Paxos algorithm

## paxos
implemented paxos algorithm including three stages: Prepare, Accept, Decide.

When call **Paxos.start()**: the peers in Paxos will achieve an agreement with accepts from majority( **>= N/2 + 1**).

## kvpaxos
implemented a server-clients model with <String, Integer> Map stored in the server. Everytime the client send operation request: **Put** or **Get**,
it must get consencus from paxos algorithm first, for getting the latest value with an input key since other clients are likely to put other values in the same key.

# Fault-tolerant key-value store based on Paxos algorithm

## paxos
implemented paxos algorithm including three stages: Prepare, Accept, Decide.

When call Paxos.start(): the peers in Paxos will achieve an agreement with accepts from majority( >= N/2 + 1).

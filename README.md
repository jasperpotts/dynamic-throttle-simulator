# dynamic-throttle-simulator
Simulator for experimenting with design ideas for dynamic transaction throttling for Hiero Consensus Node
### Aim: Model enough of Hedera to test the throttling algorithms
It simulates a load generator creating transactions with random execution time costs and sends the
transaction to a random node. The nodes apply throttling of intake rate based on the load of the system using a new
algorithm. The accepted transactions are queued up and built into events. The events are then pretend gossiped to other
nodes but actually sent to central Consensus simulator that just uses a single concurrent queue to simulate the
Hashgraph algorithm. The events are grouped into rounds and given consensus timestamps. The resulting rounds are then
delivered to all nodes. With those rounds, they compute the health of the system and use it to adjust the throttling.
The rounds are also executed on a single thread in each node to simulate Hedera's execution model. The thread just
sleeps an amount of time for each transaction based on its execution time cost. 

The aim is to design a throttle algorithm to make sure it produces stable healthy nodes and consistent back pressure
to the load generator.
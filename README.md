1. Μεταγλώττιση (Compilation)
javac *.java
(Δεν παίρνει παραμέτρους, απλά "χτίζει" όλα τα .class αρχεία)

2. Secure Random Generator (SRG)
java -cp . SecureRandomServer 7000
<SRG_PORT>

3. Reducer
java -cp . Reducer 6100
<REDUCER_PORT>

4. Workers
(Εκτέλεση σε 3 διαφορετικά τερματικά)

java -cp . Worker 5000 127.0.0.1 7000
<WORKER_PORT> <SRG_IP> <SRG_PORT>

java -cp . Worker 5001 127.0.0.1 7000
<WORKER_PORT> <SRG_IP> <SRG_PORT>

java -cp . Worker 5002 127.0.0.1 7000
<WORKER_PORT> <SRG_IP> <SRG_PORT>

5. Master
(Εδώ θέλει προσοχή στα κόμματα χωρίς κενά στη λίστα των Workers)

java -cp . Master 3 6000 127.0.0.1 6100 127.0.0.1:5000,127.0.0.1:5001,127.0.0.1:5002
<NUM_WORKERS> <MASTER_PORT> <REDUCER_IP> <REDUCER_PORT> <WORKER_ENDPOINTS>

6. Manager Console
java -cp . ManagerConsole 127.0.0.1 6000
<MASTER_IP> <MASTER_PORT>

Εντολή προσθήκης παιχνιδιού (μέσα στην κονσόλα):
add roulette provider1 3 10 logo.png 0.1 10 low key123
<name> <provider> <stars> <votes> <logo_path> <min_bet> <max_bet> <risk> <secret>

7. Player Console (Dummy)
java -cp . DummyPlayerConsole 127.0.0.1 6000
<MASTER_IP> <MASTER_PORT>

Εντολή πονταρίσματος (μέσα στην κονσόλα):
play user123 roulette 5
<playerId> <gameName> <betAmount>






java -cp . Master 3 6000 127.0.0.1 6100 127.0.0.1:5000,127.0.0.2:5001,127.0.0.3:5002   
παραδειγμα αλλαγης ip για workers
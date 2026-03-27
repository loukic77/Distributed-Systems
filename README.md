# Distributed-Systems
Distributed Systems Project AUEB 


How to Use

start java SecureRandomServer 7000
start java Reducer 6100
timeout /t 1
start java Worker 5000 127.0.0.1 7000
start java Worker 5001 127.0.0.1 7000
start java Worker 5002 127.0.0.1 7000
timeout /t 2
start cmd /k java -cp . Master 3 6000 127.0.0.1 6100

# Manager UI
start cmd /k "java -cp . ManagerConsole 127.0.0.1 6000"

# Player UI (dummy)
start cmd /k "java -cp . DummyPlayerConsole 127.0.0.1 6000"
# Distributed-Systems
Distributed Systems Project AUEB

## Build

```cmd
javac *.java
```

## Quick Start (One Machine)

```cmd
start "SRG" cmd /k java -cp . SecureRandomServer 7000
start "Reducer" cmd /k java -cp . Reducer 6100
timeout /t 2
start "Worker-0" cmd /k java -cp . Worker 5000 127.0.0.1 7000
start "Worker-1" cmd /k java -cp . Worker 5001 127.0.0.1 7000
start "Worker-2" cmd /k java -cp . Worker 5002 127.0.0.1 7000
timeout /t 2
start "Master" cmd /k java -cp . Master 3 6000 127.0.0.1 6100 127.0.0.1:5000,127.0.0.1:5001,127.0.0.1:5002
```

## Dynamic Worker Endpoints

Master supports worker endpoints with this extra argument:

```text
workerEndpoints = host1:port1,host2:port2,...
```

Rules:
- Number of endpoints must equal `numWorkers`.
- Each endpoint must be `host:port`.

Example with remote workers:

```cmd
java -cp . Master 3 6000 10.0.0.5 6100 10.0.0.11:5000,10.0.0.12:5001,10.0.0.13:5002
```

## Consoles

Manager UI:

```cmd
start "Manager" cmd /k java -cp . ManagerConsole 127.0.0.1 6000
```

Player UI (dummy):

```cmd
start "Player" cmd /k java -cp . DummyPlayerConsole 127.0.0.1 6000
```

## Convenience Script

You can also run:

```cmd
run.bat
```

Edit configuration variables inside `run.bat` to change hosts, ports, and worker endpoints.
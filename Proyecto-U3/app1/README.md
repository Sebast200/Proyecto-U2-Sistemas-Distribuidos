para ejecutar app1
docker compose up -d galera-node-1
sleep 10
docker compose up -d galera-node-2 galera-node-3 proxysql backend frontend
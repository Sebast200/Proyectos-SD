set -e

echo "Waiting for master to be ready..."
until mysql -h mysql-master -u root -prootpass -e "SELECT 1" &> /dev/null
do
  echo "Master not ready, waiting..."
  sleep 2
done

echo "Configuring replica..."
mysql -u root -prootpass <<-EOSQL
  STOP SLAVE;
  CHANGE MASTER TO
    MASTER_HOST='mysql-master',
    MASTER_USER='repl',
    MASTER_PASSWORD='repl_password',
    MASTER_AUTO_POSITION=1;
  START SLAVE;
EOSQL

echo "Replica configured successfully"
import btcchina
access_key="INPUT ACCESS KEY"
secret_key="INPUT SECRET KEY"
bc = btcchina.BTCChina(access_key,secret_key)
result = bc.get_account_info()
print result

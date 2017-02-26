#!/usr/bin/env python2
#
# Demonstrates how to use the conditional close functionality; that is,
# placing an order that, once filled, will place another order.
#
# This can be useful for very simple automation, where a bot is not
# needed to constantly monitor execution.

# method
# number of name-value pairs
# name1 value1
# ...
# name$n value$n

import krakenex

k = krakenex.API()
k.load_key('kraken.key')

f = open('kraken_input.txt', 'r')

# read method name and number of params
method = f.readline().strip('\n')
num = int(f.readline().strip('\n'))
#print('method = ' + method)
#print('num = ' + str(num))

# for each param, get the name-value pair
params = {}
for i in range(0, num):
    name, value = [x for x in f.readline().split()]
    #print(name + ',' + value)
    params[name] = value
    
k.query_private(method, params)

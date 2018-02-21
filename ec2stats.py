#----------------------------------------------------------------------------
# Copyright 2018, FittedCloud, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# 
# Author: Jin Ren (jin@fittedcloud.com)
# Date: 2/21/2018
#----------------------------------------------------------------------------

import sys
import json
import datetime
import arrow
import argparse
import boto3
import botocore
import requests
from hashlib import sha256
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)
logging.getLogger('boto3').setLevel(logging.WARNING)
logging.getLogger("botocore").setLevel(logging.WARNING)

ACCESS_KEY = ''
SECRET_ACCESS = ''
REGION_LIST = [
    'us-east-1',       # US East (N. Virginia)
    'us-west-2',       # US West (Oregon)
    'us-west-1',       # US West (N. California)
    'us-east-2',       # US East (Ohio)
    ]
SERVER_URL = 'https://customer.fittedcloud.com/v1/ec2stats'    
days = 14
period = 900    # 15 minutes

def CollectCpuStats(cw, instanceId, days, period):
    '''
    :param cw: cloudwatch connection
    :param instanceId: ec2 instance id
    :param days: number of days to collect stats
    :param period: time in seconds between each sample
    :return: raw cloudwatch metric data
    '''
    now = arrow.utcnow()
    now = now.replace(minute=(now.minute/(period/60)*(period/60)), second=0)
    eTime = now.strftime("%Y-%m-%d %H:%M:%S")
    sTime = now.replace(days=-days).strftime("%Y-%m-%d %H:%M:%S")

    try:                                
        res = cw.get_metric_statistics(Namespace='AWS/EC2',
                                       MetricName='CPUUtilization',
                                       Dimensions=[{'Name': 'InstanceId', 'Value': instanceId}],
                                       StartTime=sTime,
                                       EndTime=eTime,
                                       Period=period,
                                       Statistics=['Average', 'Maximum'],
                                       Unit='Percent')
    except botocore.exceptions.ClientError as e:
        logger.error("Failed to call get_metric_statistics %s" %(e.response['Error']['Message']))
        return []
    except:
        logger.exception("Failed to call get_metric_statistics")
        return []
    return res

def CollectCpuStatsAll(regions, accessKey, secretAccess):
    '''
    :param regions: list of regions
    :param accessKey: access key
    :param secretAccess: secret access key
    :return: list of instances along with cloudwatch stats
    '''
    instances = {'Instances':[], 'OwnerId':'', 'Threshold':{'Avg':5, 'Max':30}}
    for region in regions:
        try:
            ec2Client = boto3.client('ec2', aws_access_key_id=accessKey, aws_secret_access_key=secretAccess, region_name=region)
            cw = boto3.client('cloudwatch', region_name=region, aws_access_key_id=accessKey, aws_secret_access_key=secretAccess)
        except botocore.exceptions.ClientError as e:
            logger.error("Failed to connect %s" %(e.response['Error']['Message']))
            return []
        except:
            logger.exception("Failed to connect")
            return []

        try:
            response = ec2Client.describe_instances(DryRun = False, InstanceIds=[], Filters=[])
        except botocore.exceptions.ClientError as e:
            logger.error("Failed to call describe instances %s" %(e.response['Error']['Message']))
            return []
        except:
            logger.exception("Failed to call get_metric_statistics")
            return []

        for reservation in response['Reservations']:
            if not instances['OwnerId']:
                instances['OwnerId'] = sha256(reservation['OwnerId'] if 'OwnerId' in reservation else '0').hexdigest()[:16]
                
            for instance in reservation['Instances']:
                instanceState = instance['State']
                if instanceState['Name'] != 'terminated':
                    ec2 = { 'Region':region, 
                            'InstanceId': instance['InstanceId'],
                            'InstanceType': instance['InstanceType'],
                            'State': instance['State'],
                            'Tags': instance['Tags']}
                    ec2['Stats'] = CollectCpuStats(cw, instance['InstanceId'], days, period)
                    instances['Instances'].append(ec2)
    return instances

def DatetimeConverter(t):
    '''
    Serialize datetime object in stats
    '''
    if isinstance(t, datetime.datetime):
        return t.__str__()

def SaveObject(obj, prefix):
    '''
    Save object as json file
    :param obj: object
    :param prefix: prefix for file
    '''
    if not obj:
        return
    fileName = prefix + '-' + arrow.utcnow().strftime("%Y-%m-%d") + '.json'
    with open(fileName, 'w') as fp:
        try:
            json.dump(obj, fp, default=DatetimeConverter)
            print("%s saved to %s" %(prefix, fileName))
        except:
            logger.exception("Failed to write result to file")

def PrintInstanceRegions(summary):
    if 'Regions' not in summary:
        return
    regions = summary['Regions']
    print("\n{0}Distribution of Regions{1}".format('-'*35, '-'*35))
    s = ''
    for i, t in enumerate(sorted(regions, key=lambda x: x[1], reverse=True)):
        s += ("{:10s}:{:4d}".format(t[0], t[1]))
        if i != 0 and i%4 == 0:
            print("{}".format(s))
            s = ''
        else:
            s += " | "
    if s:
        print("{}".format(s)) 

def PrintInstanceTypes(summary):
    if 'InstanceTypes' not in summary:
        return
    instanceTypes = summary['InstanceTypes']
    print("\n{0}Distribution of Instance Types{1}".format('-'*32, '-'*31))
    s = ''
    for i, t in enumerate(sorted(instanceTypes, key=lambda x: x[1], reverse=True)):
        s += ("{:10s}:{:4d}".format(t[0], t[1]))
        if i != 0 and i%4 == 0:
            print("{}".format(s))
            s = ''
        else:
            s += " | "
    if s:
        print("{}".format(s))

def PrintEfficiency(summary):
    if 'Efficiency' not in summary:
        return
    efficiency = summary['Efficiency']
    print("\n{0}   Efficiency Compared to Users with Monthy Spending Around ${1:10s}{2}".format('-'*11, efficiency['CostLevel'], '-'*11))
    efficiencies = []
    efficiencies.append(['Average', efficiency['Average']])
    efficiencies.append(['Efficient Users', efficiency['Efficient']])
    efficiencies.append(['Your Efficiency', efficiency['Efficiency']])
    efficiencies.sort(key=lambda x: x[1])
    s = ''
    for i, e in enumerate(efficiencies):
        s += ("{:20s}:{:8d}".format(e[0], e[1]))
        if i<len(efficiencies)-1:
            s += " | "
    print(s)

def PrintUnderUtilized(summary):
    if 'UnderUtilized' not in summary:
        return
    underutilized = summary['UnderUtilized']
    print("\n{0}Under-Utilized Instances: Avg<={1}%, Max<={2}%{3}".format('-'*25, summary['Threshold']['Avg'], summary['Threshold']['Max'], '-'*24))
    s = ''
    for i in underutilized:
        print("{:20s}:{:10s}".format(i[0], i[1]))
        
def PrintSummary(result):
    if 'Summary' not in result:
        return
    summary = result['Summary']

    for metric in ['Average', 'Maximum']:
        print("\n{0}{1} CPU Utilization{2}".format('-'*35, metric, '-'*35))
        print("{:^52}|{:^40}".format('Distribution','Summary'))
        k,v = 'CPU%     : ','Instances: '
        for i in summary[metric]['Histogram']:
            k += "{:<4d}".format(i[0])
            v += "{:<4d}".format(i[1])

        k += ' | '
        v += ' | '
        for i in ['Min', 'Max', 'Mean', '<=5%', '<=10%', '<=30%']:
            k += "{:<7s}".format(i)
            v += "{:<7s}".format(str(summary[metric][i]))
        
        print(k+"\n"+v)
        
    PrintInstanceTypes(summary)
    PrintInstanceRegions(summary)
    PrintEfficiency(summary)
    PrintUnderUtilized(summary)

def AnalyzeStats(instances, url, verbose, threshold):
    '''
    Send stats to server and get save result.
    :param instances: ec2 instances with stats
    :param url: server address
    :param verbose: print result on screen
    '''
    headers = {'Content-type': 'application/json'}
    instances['Threshold']['Avg'] = int(threshold[0])
    instances['Threshold']['Max'] = int(threshold[1])
    response = requests.post(url, data=json.dumps(instances,default=DatetimeConverter), headers=headers, verify=False)
    result = response.json()
    SaveObject(result, 'ec2summary')
    if verbose:
        PrintSummary(result)

def LoadStatsFile(fileName):
    '''
    Load existing stats file
    :param fileName:
    :return:
    '''
    try:
        with open(fileName) as f:
            instances = json.load(f)
            f.close()
    except:
        logger.exception("Failed to open %s" %fileName)
        return []
    return instances

def ParseArgs(arg):
    parser = argparse.ArgumentParser()
    parser.add_argument("-k", "--access_key", dest="accessKey", help="access key", default=ACCESS_KEY, required=False)
    parser.add_argument("-s", "--secret_key", dest="secretAccess", help="secret access key", default=SECRET_ACCESS, required=False)
    parser.add_argument("-u", "--url", dest="url", help="api server url", default=SERVER_URL, required=False)
    parser.add_argument("-a", "--analyze", dest="analyze", help="send stats to server to analyze", type=bool, default=True, required=False)
    parser.add_argument("-l", "--load_stats", dest="loadStats", help="stats file name to load data from", default='', required=False)
    parser.add_argument("-v", "--verbose", dest="verbose", help="print summary details", type=bool, default=True, required=False)
    parser.add_argument("-t", "--threshold", dest="threshold", help="[average, max] CPU threshold ", nargs=2, default=[5,30], required=False)
    args = parser.parse_args()
    return args        
        
if __name__ == "__main__":

    args = ParseArgs(sys.argv[1:])
    accessKey, secretAccess = args.accessKey, args.secretAccess

    if args.loadStats:
        instances = LoadStatsFile(args.loadStats)
    else:
        while not accessKey:
            accessKey = raw_input('Access Key:')
        while not secretAccess:
            secretAccess = raw_input('Secret Access Key:')
        instances = CollectCpuStatsAll(REGION_LIST, accessKey, secretAccess)
        SaveObject(instances, 'ec2stats')
        
    if args.analyze:
        AnalyzeStats(instances, args.url, args.verbose, args.threshold)
    

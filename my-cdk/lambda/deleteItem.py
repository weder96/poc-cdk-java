import json
import boto3
from boto3 import dynamodb
from boto3.dynamodb.conditions import Key, Attr


def lambda_handler(event, context):
    print('Starting input lambda function call')
    print(event['pathParameters']['id'])

    idParam = event['pathParameters']['id']
    client = boto3.client('dynamodb')

    item = client.query(
        ExpressionAttributeValues={
            ':vId': {
                'S': str(idParam)
            },
        },
        KeyConditionExpression='orderId = :vId',
        TableName='orders'
    )

    if len(item["Items"]) > 0:
        print(item["Items"][0]["orderDate"]["S"])
        deleteItem = client.delete_item(
            TableName='orders' ,
            Key={
                'orderId': { 'S' : str(idParam)},
                'orderDate': { 'S' : str(item["Items"][0]["orderDate"]["S"]) }
            }
        )

        print("The query returned the following item:")
        print(deleteItem)
        response = {
            'statusCode': 200,
            'body': 'Item delete with Successfully :'+ str(json.dumps(deleteItem)),
            'headers': { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*'}
        }

        return response
    else:
        raise Exception(str({"message": "orderId Not Found "+ str(idParam)}))

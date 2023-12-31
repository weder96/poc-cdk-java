package com.myorg;

import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.apigateway.*;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.dynamodb.TableProps;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.lambda.*;
import software.amazon.awscdk.services.lambda.Runtime;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class CreateDynamoDb {
    public CreateDynamoDb(MyStack myStack) {
        cdkDynamodb(myStack);
    }

    public  void cdkDynamodb(MyStack myStack){
        TableProps tableProps = createTablePropsDynamoDB(myStack);
        Table dynamodbTable = new Table(myStack, "orders", tableProps);
        Map<String, String> lambdaEnvMap = createLambdaEnvMap(dynamodbTable);

        Role role = createLambdaRole(myStack);

        Function createItemFunction = new Function(myStack, "createItemFunction", getLambdaFunctionProps(role,  "createItem","py"));
        Function updateItemFunction = new Function(myStack, "updateItemFunction", getLambdaFunctionProps(role,  "updateItem","py"));
        Function deleteItemFunction = new Function(myStack, "deleteItemFunction", getLambdaFunctionProps(role,  "deleteItem","py"));
        Function getOneItemFunction = new Function(myStack, "getOneItemFunction", getLambdaFunctionProps(role, "getOneItem","py"));
        Function getAllItemsFunction = new Function(myStack, "getAllItemsFunction", getLambdaFunctionProps(role,  "getAllItems","py"));

        dynamodbTable.grantReadWriteData(createItemFunction);
        dynamodbTable.grantReadWriteData(updateItemFunction);
        dynamodbTable.grantReadWriteData(deleteItemFunction);
        dynamodbTable.grantReadWriteData(getOneItemFunction);
        dynamodbTable.grantReadWriteData(getAllItemsFunction);

        RestApi api = new RestApi(myStack, "itemsApi", RestApiProps.builder().restApiName("Items Service").build());

        IResource items = api.getRoot().addResource("items");

        Integration getAllIntegration = new LambdaIntegration(getAllItemsFunction);
        items.addMethod("GET", getAllIntegration);

        Integration createOneIntegration = new LambdaIntegration(createItemFunction);
        items.addMethod("POST", createOneIntegration);
        addCorsOptions(items);

        IResource singleItem = items.addResource("{id}");
        Integration getOneIntegration = new LambdaIntegration(getOneItemFunction);
        singleItem.addMethod("GET",getOneIntegration);

        Integration updateOneIntegration = new LambdaIntegration(updateItemFunction);
        singleItem.addMethod("PATCH",updateOneIntegration);

        Integration deleteOneIntegration = new LambdaIntegration(deleteItemFunction);
        singleItem.addMethod("DELETE",deleteOneIntegration);
        addCorsOptions(singleItem);
    }

    @NotNull
    private static Map<String, String> createLambdaEnvMap(Table dynamodbTable) {
        Map<String, String> lambdaEnvMap = new HashMap<>();
        lambdaEnvMap.put("TABLE_NAME", dynamodbTable.getTableName());
        lambdaEnvMap.put("PRIMARY_KEY","orderId");
        return lambdaEnvMap;
    }

    // The default removal policy is RETAIN, which means that cdk destroy will not attempt to delete
    // the new table, and it will remain in your account until manually deleted. By setting the policy to
    // DESTROY, cdk destroy will delete the table (even if it has data in it)
    private TableProps createTablePropsDynamoDB(MyStack myStack) {
        TableProps tableProps;
        Attribute partitionKey = Attribute.builder().name("orderId").type(AttributeType.STRING).build();
        Attribute sortKey = Attribute.builder().name("orderDate").type(AttributeType.STRING).build();
        tableProps = TableProps.builder().tableName("orders")
                                         .partitionKey(partitionKey)
                                         .sortKey(sortKey).removalPolicy(RemovalPolicy.DESTROY).build();
        return tableProps;
    }


    private void addCorsOptions(IResource item) {
        List<MethodResponse> methoedResponses = new ArrayList<>();

        Map<String, Boolean> responseParameters = new HashMap<>();
        responseParameters.put("method.response.header.Access-Control-Allow-Headers", Boolean.TRUE);
        responseParameters.put("method.response.header.Access-Control-Allow-Methods", Boolean.TRUE);
        responseParameters.put("method.response.header.Access-Control-Allow-Credentials", Boolean.TRUE);
        responseParameters.put("method.response.header.Access-Control-Allow-Origin", Boolean.TRUE);
        methoedResponses.add(MethodResponse.builder()
                .responseParameters(responseParameters)
                .statusCode("200")
                .build());
        MethodOptions methodOptions = MethodOptions.builder()
                .methodResponses(methoedResponses)
                .build();

        Map<String, String> requestTemplate = new HashMap<>();
        requestTemplate.put("application/json","{\"statusCode\": 200}");
        List<IntegrationResponse> integrationResponses = new ArrayList<>();

        Map<String, String> integrationResponseParameters = new HashMap<>();
        integrationResponseParameters.put("method.response.header.Access-Control-Allow-Headers","'Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token,X-Amz-User-Agent'");
        integrationResponseParameters.put("method.response.header.Access-Control-Allow-Origin","'*'");
        integrationResponseParameters.put("method.response.header.Access-Control-Allow-Credentials","'false'");
        integrationResponseParameters.put("method.response.header.Access-Control-Allow-Methods","'OPTIONS,GET,PUT,POST,DELETE'");
        integrationResponses.add(IntegrationResponse.builder()
                .responseParameters(integrationResponseParameters)
                .statusCode("200")
                .build());
        Integration methodIntegration = MockIntegration.Builder.create()
                .integrationResponses(integrationResponses)
                .passthroughBehavior(PassthroughBehavior.NEVER)
                .requestTemplates(requestTemplate)
                .build();

        item.addMethod("OPTIONS", methodIntegration, methodOptions);
    }

    private FunctionProps getLambdaFunctionProps(Role role, String nameFunction, String extension) {
        try{
            String lambdaContent = readFileAsString("./lambda/".concat(nameFunction).concat(".").concat(extension));
               return  FunctionProps.builder()
                        .description("Lambda ".concat(nameFunction))
                        .code(Code.fromInline(lambdaContent))
                        .handler("index.lambda_handler")
                        .role(role)
                        .memorySize(512)
                        .timeout(Duration.seconds(300))
                        .runtime(Runtime.PYTHON_3_10)
                        .build();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String readFileAsString(String fileName) throws Exception {
        String data = "";
        try {
            data = new String(Files.readAllBytes(Paths.get(fileName)), "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return data;
    }

    public Role createLambdaRole(MyStack myStack){
        PolicyStatement statement2 = PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(Arrays.asList(new String[] {"logs:CreateLogGroup","logs:CreateLogStream","logs:PutLogEvents"}))
                .resources(Arrays.asList(new String[] {"arn:aws:logs:*:*:*"})).build();

        PolicyDocument policyDocument = PolicyDocument.Builder.create()
                .statements(Arrays.asList(new PolicyStatement[]{statement2})).build();


        return Role.Builder.create(myStack,"LambdaIAMRole")
                .roleName("LambdaIAMRole")
                .inlinePolicies(Collections.singletonMap("key", policyDocument))
                .path("/")
                .assumedBy(new ServicePrincipal("lambda.amazonaws.com")).build();
    }
}

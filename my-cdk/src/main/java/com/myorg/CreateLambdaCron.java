package com.myorg;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.apigateway.LambdaRestApi;
import software.amazon.awscdk.services.events.Rule;
import software.amazon.awscdk.services.events.Schedule;
import software.amazon.awscdk.services.events.targets.LambdaFunction;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.SingletonFunction;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

public class CreateLambdaCron {
    public CreateLambdaCron(MyStack myStack) {
        loadLambdaCron(myStack);
    }

    private void loadLambdaCron(MyStack myStack){
        try{
            //create lambda, roles and permissions
            PolicyStatement statement2 = PolicyStatement.Builder.create()
                    .effect(Effect.ALLOW)
                    .actions(Arrays.asList(new String[] {"logs:CreateLogGroup","logs:CreateLogStream","logs:PutLogEvents"}))
                    .resources(Arrays.asList(new String[] {"arn:aws:logs:*:*:*"})).build();

            PolicyDocument policyDocument = PolicyDocument.Builder.create()
                    .statements(Arrays.asList(new PolicyStatement[]{statement2})).build();


            Role lambdaRole = Role.Builder.create(myStack,"LambdaIAMRole")
                    .roleName("LambdaIAMRole")
                    .inlinePolicies(Collections.singletonMap("key", policyDocument))
                    .path("/")
                    .assumedBy(new ServicePrincipal("lambda.amazonaws.com")).build();



            String lambdaContent = readFileAsString("./lambda/app.py");

            SingletonFunction lambdaFunction =
                    SingletonFunction.Builder.create(myStack, "cdk-lambda-cron")
                            .description("Lambda which prints \"I'm running\"")
                            .code(Code.fromInline(lambdaContent))
                            .handler("index.lambda_handler")
                            .role(lambdaRole)
                            .memorySize(512)
                            .timeout(Duration.seconds(300))
                            .runtime(Runtime.PYTHON_3_10)
                            .uuid(UUID.randomUUID().toString())
                            .build();


            // Defines an API Gateway REST API resource backed by our "hello" function
            LambdaRestApi.Builder.create(myStack, "Endpoint")
                    .handler(lambdaFunction)
                    .build();

            Rule rule = Rule.Builder.create(myStack, "cdk-lambda-cron-rule")
                    .description("Run every day at 6PM UTC")
                    .schedule(Schedule.expression("cron(0 18 ? * MON-FRI *)"))
                    .build();

            rule.addTarget(new LambdaFunction(lambdaFunction));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // function to read the file content
    public String readFileAsString(String fileName) throws Exception {
        String data = "";
        try {
            data = new String(Files.readAllBytes(Paths.get(fileName)), "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return data;
    }
}

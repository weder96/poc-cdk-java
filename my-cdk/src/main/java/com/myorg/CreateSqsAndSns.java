package com.myorg;


import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.SingletonFunction;
import software.amazon.awscdk.services.lambda.eventsources.SqsEventSource;
import software.amazon.awscdk.services.s3.notifications.SqsDestination;
import software.amazon.awscdk.services.ses.actions.EmailEncoding;
import software.amazon.awscdk.services.ses.actions.Sns;
import software.amazon.awscdk.services.sns.Subscription;
import software.amazon.awscdk.services.sns.SubscriptionProtocol;
import software.amazon.awscdk.services.sns.Topic;
import software.amazon.awscdk.services.sns.subscriptions.SqsSubscription;
import software.amazon.awscdk.services.sqs.DeadLetterQueue;
import software.amazon.awscdk.services.sqs.Queue;
import software.amazon.awscdk.services.sqs.QueueProps;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.logging.Logger;


public class CreateSqsAndSns {
    private static Logger logger = Logger.getLogger(CreateSqsAndSns.class.getName());
    public CreateSqsAndSns(MyStack myStack) {
        cdkSqs(myStack);
    }

    public  void cdkSqs(MyStack myStack){
        logger.info("new sqs to sns ");
        // 👇 create queue
        Queue dQueue = Queue.Builder.create(myStack, "cdk-dead-letter-queue-id")
                .retentionPeriod(Duration.days(7))
                .build();

        DeadLetterQueue deadLetterQueue = DeadLetterQueue.builder().queue(dQueue)
                .maxReceiveCount(1)
                .build();

        Queue sQueue = Queue.Builder.create(myStack, "cdk-upload-queue-id")
                .deadLetterQueue(deadLetterQueue)
                .visibilityTimeout(Duration.seconds(300))
                .build();

        // 👇 create sns topic
        Topic snsTopic = Topic.Builder.create(myStack,"cdk-sns-topic")
                                            //.topicName("cdk-sns-topic")
                                            //.fifo(true)
                                            .build();

        // 👇 create Subscription add Topic
        SqsSubscription sqsSubscription = SqsSubscription.Builder.create(sQueue).build();
        snsTopic.addSubscription(sqsSubscription);

        // 👇 create Lambda Function
        SingletonFunction function =  createLambdaFunction(myStack);
        function.addEventSource(SqsEventSource.Builder.create(sQueue).build());

    }

    public SingletonFunction createLambdaFunction(MyStack myStack){
        try{
            PolicyStatement statement2 = PolicyStatement.Builder.create()
                    .effect(Effect.ALLOW)
                    .actions(Arrays.asList(new String[] {"logs:CreateLogGroup","logs:CreateLogStream","logs:PutLogEvents"}))
                    .resources(Arrays.asList(new String[] {"arn:aws:logs:*:*:*"})).build();

            PolicyDocument policyDocument = PolicyDocument.Builder.create()
                    .statements(Arrays.asList(new PolicyStatement[]{statement2})).build();


            Role lambdaRole = Role.Builder.create(myStack,"LambdaSqsRoleDev")
                    .roleName("LambdaSqsRoleDev")
                    .inlinePolicies(Collections.singletonMap("key", policyDocument))
                    .path("/")
                    .assumedBy(new ServicePrincipal("lambda.amazonaws.com")).build();

        String lambdaContent = CreateLambdaCron.readFileAsString("./lambdaSqs/app.py");

        return SingletonFunction.Builder.create(myStack, "cdk-lambda-sqs")
                        .description("Lambda which prints \"I'm running\"")
                        .code(Code.fromInline(lambdaContent))
                        .handler("index.lambda_handler")
                        .role(lambdaRole)
                        .memorySize(512)
                        .timeout(Duration.seconds(300))
                        .runtime(Runtime.PYTHON_3_10)
                        .uuid(UUID.randomUUID().toString())
                        .build();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }



}

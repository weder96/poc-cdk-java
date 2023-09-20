package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public class MyCdkApp {
    public static void main(final String[] args) {
        App app = new App();
        StackProps stackProps = StackProps.builder().env(Environment.builder()
                .account("710304818543")
                .region("us-east-1")
                .build()).build();
        new MyStack(app, "dev", stackProps);
        app.synth();
    }
}


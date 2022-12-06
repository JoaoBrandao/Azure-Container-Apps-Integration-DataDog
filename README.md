# Integration between Apps in Azure Container Apps and DataDog Agent

Currently Azure Container Apps does not natively support the option to have a DataDog agent running inside the environment, so here I found an easier option to have this integration done using Azure Kubernetes to host the DataDog Agent and connect our applications running on Azure Container Apps with the DataDog Agent.

<br>Therefore, in the documentation below we will find two sections, one for installing DataDog Agent on Azure Kubernetes and one for integrating our application with the agent

<br><strong>Note:</strong>
I have tried also Azure Container Instances but the block (docker volumes) is the same as Azure Container Apps.

## Setup DataDog agent in Azure Kubernetes (AKS)
In this section, we will cover how we install the DataDog agent in our environment and all the steps required to expose an IP to be assigned to your application.

### Requisites:
1. Install Helm in your machine (https://helm.sh/docs/intro/install/)
2. Create a basic Azure Kubernetes with a public IP

    2.1 For Tests you can use the default configurations from AKS 

3. Basic knowledge of Kubernetes would be necessary


### Install DataDog Agent

DataDog company provide a helm code that allow us to setup our DD agent without adding complexity. 

The first step would be to install datadog helm code in your machine by running the following command:

```
helm repo add datadog https://helm.datadoghq.com
helm repo update
```

DataDog Helm Documentation: https://github.com/DataDog/helm-charts/tree/main/charts/datadog

Setup a local account in Cluster Configurations.
 ![aks-local](/assets/aks-local-acc.png)


Then you must connect to your AKS using AZ CLI, assuming that you have already authenticated and selected your subscription you must get your az command line from Portal.
 ![aks-connect](/assets/aks-connect.png)


The next step would be to create a <strong>tags.yml</strong> file that will be used when executing our helm code. This file will include all the tags that will be aggregated for all data that will be pushed from agent to DataDog.

```
datadog:
 tags:
    - "team:demo"
    - "env:dev"
    - "datadog.tags:dev"
 dogstasd: 
   tags:
     - "env:dev"
     - "datadog.tags:dev"
     - "team:demo"
```


<br>Then run the following command to setup our DataDog Agent:
```
helm install datadog \
 --set datadog.apiKey=<<DATADOG_API_KEY> \
 --set datadog.site=datadoghq.eu \
 --set agents.useHostNetwork=true \
 --set datadog.apm.enabled=true \
 --set datadog.dogstatsd.nonLocalTraffic=true \
 --set datadog.logs.enabled=true \
 --set datadog.logs.containerCollectAll=true \
-f tags.yml \
  datadog/datadog
```

<strong>To check pods state, please check the section of troubleshooting.</strong>


After this process we must create a rule inside our Kubernetes Ingress. For that, you must select the service and ingress in the left menu and then select Ingresses and create.

 ![aks-ingresses](/assets/ingresses.png)

Add the following ingress configuration in your service that as a public IP

```
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: minimal-ingress
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
    kubernetes.io/ingress.class: addon-http-application-routing
spec:
  rules:
  - http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: datadog
            port:
              number: 8126
```
This will route all traffic from our public Kubernetes address into our pod


After Completing all this steps you will be able to see three new services running in your AKS that handles all traffic coming to DataDog Agent.

 ![aks-pods](/assets/pods-status.png)


Last step would be to get the External IP from your AKS services and use it in your applications
 ![public-ip](/assets/public-ip.png)


### Configuration of Ingresses

### Troubleshooting

#### Get pods
```
kubectl get pods
```
 ![aks-pods](/assets/pods.png)


#### Check logs from pod
```
kubectl logs <<POD-NAME>>
```


## Setup DataDog agent in our Applications

To setup DataDog agent in our application we would need to add two configurations in our Docker File and add a jar file provided by DataDog Company.

The first step is to download jar file from the link: https://dtdg.co/latest-java-tracer and add the file in your application resources.

After completing this step you will need to add two configurations in your DockerFile. 

1. Copy jar file into our docker image
```
COPY --chown=185 target/classes/datadog-agent/*.jar /deployments/datadog/
```
2. Set javaagent configurations:

```
ENV JAVA_OPTS="-javaagent:/deployments/datadog/dd-java-agent.jar -Ddd.agent.host=<<DATADOG_AGENT_IP>> -Ddd.profiling.enabled=true -XX:FlightRecorderOptions=stackdepth=256 -Ddd.logs.injection=true -Ddd.service=demo-service -Ddd.version=1.0 -Ddd.env=dev"
```

After completing this two steps you will have a dockerfile like this:

### Code
```
FROM registry.access.redhat.com/ubi8/openjdk-17:1.14

ENV LANGUAGE='en_US:en'

COPY --chown=185 target/quarkus-app/lib/ /deployments/lib/
COPY --chown=185 target/quarkus-app/*.jar /deployments/
COPY --chown=185 target/quarkus-app/app/ /deployments/app/
COPY --chown=185 target/quarkus-app/quarkus/ /deployments/quarkus/
COPY --chown=185 target/classes/datadog-agent/*.jar /deployments/datadog/

EXPOSE 8080
USER 185
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -javaagent:/deployments/datadog/dd-java-agent.jar -Ddd.agent.host=<<DATADOG_AGENT_IP>> -Ddd.profiling.enabled=true -XX:FlightRecorderOptions=stackdepth=256 -Ddd.logs.injection=true -Ddd.service=demo-service -Ddd.version=1.0 -Ddd.env=dev"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"
```

<em>Docker file provided by quarkus</em></br>



### Build Application Package

After creation your solution, you will need to build your app package to be inserted in Docker image. Please run the following command:
```
mvn clean package
```

### Build a Docker image

Before executing the commands to build a docker image you will need to change Dockerfile with your configurations.

1.Replace <strong><<DATADOG_AGENT_IP>></strong> with your DataDog Agent IP
2.Replace <strong>demo-service</strong> with you application name, this will be visible in DataDog APM


```
For mac with M1:

docker build . --platform linux/amd64 -t demo-service:1.0.0

Others:

docker build . -t demo-service:1.0.0
```

### Others
After executing this steps, you will need to publish your docker image in your registry and create a new Azure Container Apps revision and use that version.







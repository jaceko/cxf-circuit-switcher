cxf-circuit-switcher [![Build Status](https://buildhive.cloudbees.com/job/jaceko/job/cxf-circuit-switcher/badge/icon)](https://buildhive.cloudbees.com/job/jaceko/job/cxf-circuit-switcher/)
==============================
CXF Circuit Switcher is an extension of the Apache CXF webservice client. It provides a failover feature inspired by the [Circuit Breaker design pattern](http://en.wikipedia.org/wiki/Circuit_breaker_design_pattern) and Michael T. Nygard's book _"Release It"_. 
The library can be used as a SOAP (JAX-WS) as well REST (JAX-RS) client feature to detect connection errors and redirect client to next available node (failover). After some (defined) time it can attempt to restore connection to a previously discarded node (failback). 
Ability to failback is a main functional improvement comparing to standard failover feature being part of Apache CXF package. 

**NOTE: This software is neither endorsed nor created by the Apache Software Foundation**

### Circuit Switcher
The webservice client is configured with one or more target endpoints in the order of preference (primary node as the first). The integration to each of remote endpoints is seen a "cicruit" which is watched by a Circuit Switcher and and when things are looking bad (the remote webservice is not responding or response time exceeds configurable _**receiveTimeout**_) we back off for some time and try next endpoint (switch to another circuit).

Each of the circuits can be in 3 states: **Closed**, **Open** and **HalfOpen**

When in **Closed** state, each call to the target endpoint is allowed. But each time it fails, a failure counter is incremented, and when the failure counter reaches a configurable _**failureThreshold**_, the circuit moves to the **Open** state.

When it moves to **Open** state, it starts a timer set to elapse at a configurable _**resetTimeout**_ value. If the timeout has not been reached, each call to the target endpoint is not allowed and next endpoint from the _**addressList**_ is selected or exception is thrown if no more endpoints available.

When the _resetTimeout_ has been reached, the circuit moves to **HalfOpen** state. In this state we are tentatively calling the target endpoint to check if it's healthy again. This means that the next call to the endpoint is allowed, but if it fails, the circuit immediately switches back to the **Open** state and the timeout period starts again. If the call to the target endpoint while in **HalfOpen** state succeeds, the circuit switches back to the +Closed+ state.

### Integration with Apache CXF
The CXF Circuit Switcher is exposed as Apache CXF's [feature](http://cxf.apache.org/docs/features.html) which is a standard way of adding capabilities to an Apache CXF based client. 
All we need to do is instantiate the _CircuitSwitcherClusteringFeature_ class setting addressList, resetTimeout, failureThreshold and receiveTimeout. Next step would be to pass it to a standard Apache CXF org.apache.cxf.jaxws.JaxWsProxyFactoryBean (SOAP) or org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean and create webservice client.

*Examples:*

Spring:
```
<bean id="clientFactory" class="org.apache.cxf.jaxws.JaxWsProxyFactoryBean">
	<property name="serviceClass"
		value="com.github.jaceko.SomeServiceInterface" />
	<property name="features">
		<util:list>
			<bean class="org.apache.cxf.clustering.CircuitSwitcherClusteringFeature">
				<property name="addressList">
					<list>
						<value>http://serverA/endpoint</value>
						<value>http://serverB/endpoint</value>
						<value>http://serverC/endpoint</value>
					</list>
				</property>
				<property name="resetTimeout" value="10000" />
				<property name="failureThreshold" value="3" />
				<property name="receiveTimeout" value="600000" />
			</bean>
		</util:list>
	</property>
</bean>
<bean id="clientTarget" factory-bean="clientFactory"
	factory-method="create" scope="prototype" lazy-init="true" />
```
Code:

```
JaxWsProxyFactoryBean bean = new JaxWsProxyFactoryBean();
bean.setServiceClass(SomeServiceInterface.class);
CircuitSwitcherClusteringFeature cbcFeature = new CircuitSwitcherClusteringFeature();
List<String> addresses =new ArrayList<String>();
adresses.add("http://serverA/endpoint");
adresses.add("http://serverB/endpoint");
adresses.add("http://serverC/endpoint");
cbcFeature.setAddressList(adresses);
cbcFeature.setFailureThreshold(3);
cbcFeature.setResetTimeout(10000);
cbcFeature.setReceiveTimeout(600000l);
SomeServiceInterface serviceClient = bean.create(SomeServiceInterface.class);
```
### Thread safety
CircuitSwitcherClusteringFeature is an extension of a standard Apache's FailoverFeature and as stated by CXF's javadoc this makes the client **not thread safe**. 
One of solutions is one suggested by Tomasz Nurkiewicz in his in his [blog post](http://nurkiewicz.blogspot.co.uk/2011/05/enabling-load-balancing-and-failover-in.html). Tomasz's idea is to use Spring and wrap the client bean (must be of _prototype_ scope) in a special proxy. Spring will then create an object pool (based on [commons-pool](http://commons.apache.org/pool) library) and create as many bean instances as necessary to keep each bean used by only one thread.
```
<bean id="client" class="org.springframework.aop.framework.ProxyFactoryBean">
        <property name="targetSource">
            <bean class="org.springframework.aop.target.CommonsPoolTargetSource">
                <property name="targetClass"
                    value="com.github.jaceko.SomeServiceInterface" />
                <property name="targetBeanName" value="clientTarget" />
                <property name="maxSize" value="20" />
                <property name="maxWait" value="5000" />
            </bean>
        </property>
</bean>
```
This means we can tell Spring not to create more than 20 clients in the pool and if the pool is empty (all the beans are currently in use), we should wait no more than 5000ms.
### Maven
Maven artifact is available in [central](http://search.maven.org/#artifactdetails|com.github.jaceko.cxf|cxf-circuit-switcher|1.0|jar):

```
<depencency>
	<groupId>com.github.jaceko.cxf</groupId>
	<artifactId>cxf-circuit-switcher</artifactId>
	<version>1.0</version>
</depencency>
```

### Maturity
The library has been production tested and has good unit and integration test coverage. 
Feel free to report any [issues](https://github.com/jaceko/cxf-circuit-switcher/issues).

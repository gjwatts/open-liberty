/*******************************************************************************
 * Copyright (c) 2017, 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.cloudant.fat;

import static com.ibm.ws.cloudant.fat.FATSuite.cloudant;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.ibm.websphere.simplicity.log.Log;

import componenttest.containers.SimpleLogConsumer;
import componenttest.topology.utils.HttpsRequest;

public class CouchDBContainer extends GenericContainer<CouchDBContainer> {

    private static final String defaultImage = "gjwatts/couchdb-tls12:1.0";
    public static final int PORT = 5984;
    public static final int PORT_SECURE = 6984;
    private String user = "dbuser";
    private String pass = "dbpass";

    /**
     * Each test class will test against a different named database schema
     * within the cloudant database. This map is a map between
     * each test class' name and it's associated database.
     */
    private HashMap<Class<?>, String> databaseNameMap = new HashMap<>();
    private HashMap<Class<?>, String> sslDatabaseNameMap = new HashMap<>();

    public CouchDBContainer() {
        this(defaultImage);
    }

    public CouchDBContainer(String image) {
        super(image);
    }

    public CouchDBContainer(ImageFromDockerfile image) {
        super(image);
    }

    public CouchDBContainer withUser(String user) {
        this.user = user;
        return this;
    }

    public String getUser() {
        return user;
    }

    public CouchDBContainer withPassword(String pass) {
        this.pass = pass;
        return this;
    }

    public String getPassword() {
        return pass;
    }

    public CouchDBContainer withDatabaseName(Class<?> clazz, String dbName, boolean useSSL) {
        this.databaseNameMap.put(clazz, dbName);
        if (useSSL) {
            this.sslDatabaseNameMap.put(clazz, dbName);
        }
        return this;
    }

    public String getDatabaseName(Class<?> clazz) {
        return databaseNameMap.get(clazz);
    }

    @Override
    protected void configure() {
        withEnv("COUCHDB_USER", user);
        withEnv("COUCHDB_PASSWORD", pass);
        withExposedPorts(PORT, PORT_SECURE);
        withLogConsumer(new SimpleLogConsumer(FATSuite.class, "cloudant"));
        waitingFor(Wait.forHttp("/").forPort(PORT).forStatusCode(200).forStatusCode(201).forStatusCode(202));
    }

    public String getURL(boolean secure) {
        return secure ? //
                        "https://" + cloudant.getContainerIpAddress() + ':' + cloudant.getMappedPort(PORT_SECURE) : //
                        "http://" + cloudant.getContainerIpAddress() + ':' + cloudant.getMappedPort(PORT);
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        try {
            createDatabases();

            int attempts = 60; // Try 60 times with a 2 second delay each time for a total of 120ish seconds
            while (!checkSSLPort() && attempts-- > 0) {
                wait(2000);
            }

            if (attempts < 1) {
                throw new RuntimeException("Could not activate SSL ports in 2 minutes");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void createDatabases() throws Exception {
        for (Map.Entry<Class<?>, String> entry : databaseNameMap.entrySet()) {
            Class<?> clazz = entry.getKey();
            String dbName = entry.getValue();

            Log.info(getClass(), "createDatabases", //
                     "Creating database for map [ " + clazz.getCanonicalName() + " , " + dbName + " ]");

            String auth = "Basic " + Base64.getEncoder().encodeToString((user + ':' + pass).getBytes());

            String response = new HttpsRequest(getURL(false) + "/" + dbName)
                            .method("PUT")
                            .allowInsecure()
                            .requestProp("Authorization", auth)
                            .requestProp("Accept", "application/json")
                            .requestProp("Content-type", "application/json")
                            .requestProp("User-Agent", "java-cloudant/unknown")
                            .expectCode(201) // HTTP 201/202 mean create successfully
                            .expectCode(202)
                            .run(String.class);
            Log.info(getClass(), "createDatabases", "Create DB response: " + response);
        }
    }

    private boolean checkSSLPort() throws Exception {
        boolean allActive = false;
        for (Map.Entry<Class<?>, String> entry : sslDatabaseNameMap.entrySet()) {
            Class<?> clazz = entry.getKey();
            String dbName = entry.getValue();

            Log.info(getClass(), "checkSSLPort", "Checking SSL connection for map [ " + clazz + " , " + dbName + " ]");

            String auth = "Basic " + Base64.getEncoder().encodeToString((user + ':' + pass).getBytes());
            HttpsRequest request = new HttpsRequest(getURL(true) + "/" + dbName)
                            .method("GET")
                            .allowInsecure()
                            .requestProp("Authorization", auth)
                            .requestProp("Accept", "application/json")
                            .requestProp("Content-type", "application/json")
                            .requestProp("User-Agent", "java-cloudant/unknown")
                            .expectCode(200);
            String response = request.run(String.class);
            Log.info(getClass(), "checkSSLPort", "Checking SSL port response: " + response);
            if (request.getResponseCode() != 200) {
                Log.info(getClass(), "checkSSLPort", "Reponse value was " + request.getResponseCode() + ", returning false");
                return false;
            }
        }

        return true; //  All SSL ports, if any, are active
    }
}

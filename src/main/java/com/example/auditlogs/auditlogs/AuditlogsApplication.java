package com.example.auditlogs.auditlogs;

import com.mongodb.MongoException;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoClient;
import com.mongodb.MongoClientSettings;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.salesforce.emp.connector.BayeuxParameters;
import com.salesforce.emp.connector.EmpConnector;
import com.salesforce.emp.connector.TopicSubscription;
import com.salesforce.emp.connector.example.BearerTokenProvider;
import org.bson.Document;
import org.eclipse.jetty.util.ajax.JSON;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;

import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.salesforce.emp.connector.LoginHelper.login;


@SpringBootApplication(exclude = {
		MongoAutoConfiguration.class,
		MongoDataAutoConfiguration.class
})
public class AuditlogsApplication {

	public static void main(String[] args) {

		try{

			/**** Connect to MongoDB ****/

			ConnectionString connString = new ConnectionString(
					"mongodb://user:shyam1983@cluster0-shard-00-00.eg9gx.mongodb.net:27017,cluster0-shard-00-01.eg9gx.mongodb.net:27017,cluster0-shard-00-02.eg9gx.mongodb.net:27017/auditlogs?ssl=true&replicaSet=atlas-jfiia7-shard-0&authSource=admin&retryWrites=true&w=majority"
			);
			MongoClientSettings settings = MongoClientSettings.builder()
					.applyConnectionString(connString)
					.retryWrites(true)
					.build();
			MongoClient mongoClient = MongoClients.create(settings);

			MongoDatabase database = mongoClient.getDatabase("auditlogs");
			MongoCollection<Document> table = database.getCollection("events");

			if (args.length < 3 || args.length > 4) {
				System.err.println("Usage: LoginExample username password topic [replayFrom]");
				System.exit(1);
			}
			long replayFrom = EmpConnector.REPLAY_FROM_EARLIEST;
			if (args.length == 4) {
				replayFrom = Long.parseLong(args[3]);
			}

			BearerTokenProvider tokenProvider = new BearerTokenProvider(() -> {
				try {
					return login(args[0], args[1]);
				} catch (Exception e) {
					e.printStackTrace(System.err);
					System.exit(1);
					throw new RuntimeException(e);
				}
			});

			BayeuxParameters params = tokenProvider.login();

			Consumer<Map<String, Object>> consumer = event ->
			{
				System.out.println(String.format("AuditlogsApplication Received:\n%s", JSON.toString(event)));
				System.out.println("AuditlogsApplication Event has started inserting in MangoDB");
				Document document = Document.parse(JSON.toString(event));
				System.out.println(String.format("AuditlogsApplication document:\n%s", document));
				table.insertOne(document);
				System.out.println("AuditlogsApplication Event has been inserted in MangoDB");
			};

			EmpConnector connector = new EmpConnector(params);

			connector.setBearerTokenProvider(tokenProvider);

			connector.start().get(5, TimeUnit.SECONDS);

			TopicSubscription subscription = connector.subscribe(args[2], replayFrom, consumer).get(5, TimeUnit.SECONDS);

			System.out.println(String.format("AuditlogsApplication Subscribed: %s", subscription));

		}
		catch (UnknownHostException e) {
			System.out.println("UnknownHostException Occurred : " + e);
			e.printStackTrace();
		} catch (MongoException e) {
			System.out.println("MongoException Occurred : " + e);
			e.printStackTrace();
		}catch (Exception e) {
			System.out.println("Exception Occurred : " + e);
			e.printStackTrace();
		}
		//SpringApplication.run(AuditlogsApplication.class, args);
	}

}

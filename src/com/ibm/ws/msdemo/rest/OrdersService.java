package com.ibm.ws.msdemo.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.transaction.UserTransaction;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import com.ibm.nosql.json.api.BasicDBList;
import com.ibm.nosql.json.api.BasicDBObject;
import com.ibm.nosql.json.util.JSON;

import com.ibm.ws.msdemo.rest.pojo.Order;

//Mapped to /orders via web.xml
@Path("/orders")
public class OrdersService {
	
	private UserTransaction utx;
	private EntityManager em;
	private Map<String, String> props = new HashMap<String, String>();
	
	public OrdersService(){
		utx = getUserTransaction();
		em = getEm();
	}
	@Context UriInfo uriInfo;
	
	//GET all orders
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response get() {
		System.out.println("Executing get orders");
			List<Order> list = em.createQuery("SELECT t FROM Order t", Order.class).getResultList();
			String json = list.toString();
			System.out.println(json);
			return Response.ok(json).build();
	}
	
	//GET a specific order
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("{id}")
	public Response get(@PathParam("id") long id) {
		System.out.println("Searching for id : " + id);
		Order order = null;
		try {
			utx.begin();
			order = em.find(Order.class, id);
			utx.commit();
		} catch (Exception e) {
			e.printStackTrace();
			return Response.status(javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR).build();
		} 
		if (order != null)
			return Response.ok(order.toString()).build();
		else
			return Response.status(javax.ws.rs.core.Response.Status.NOT_FOUND).build();
	}
	
	//new order
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public Response create(Order order) {
		System.out.println("New order: " + order.toString());
		try {
			utx.begin();
			em.persist(order);
			utx.commit();
			
			return Response.status(201).entity(String.valueOf(order.getId())).build();
		} catch (Exception e) {
			e.printStackTrace();			
			return Response.status(javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR).build();
		} finally {
			try {
				if (utx.getStatus() == javax.transaction.Status.STATUS_ACTIVE) {
					utx.rollback();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}


	}
	
	// There are two ways of obtaining the connection information for some services in Java 
	
	// Method 1: Auto-configuration and JNDI
	// The Liberty buildpack automatically generates server.xml configuration 
	// stanzas for the SQL Database service which contain the credentials needed to 
	// connect to the service. The buildpack generates a JNDI name following  
	// the convention of "jdbc/<service_name>" where the <service_name> is the 
	// name of the bound service. 
	// Below we'll do a JNDI lookup for the EntityManager whose persistence 
	// context is defined in web.xml. It references a persistence unit defined 
	// in persistence.xml. In these XML files you'll see the "jdbc/<service name>"
	// JNDI name used.

	private EntityManager getEm() {
			System.out.println("in getEm");
			getVCAPCredentials();
			//return (EntityManager) ic.lookup("java:comp/env/openjpa-order/entitymanager");
			EntityManagerFactory ef = Persistence.createEntityManagerFactory("openjpa-order", props);
			return ef.createEntityManager();
	}

	// Method 2: Parsing VCAP_SERVICES environment variable
    // The VCAP_SERVICES environment variable contains all the credentials of 
	// services bound to this application. You can parse it to obtain the information 
	// needed to connect to the SQL Database service. SQL Database is a service
	// that the Liberty buildpack auto-configures as described above, so parsing
	// VCAP_SERVICES is not a best practice.
	
	// see HelloResource.getInformation() for an example
	
	private UserTransaction getUserTransaction() {
		InitialContext ic;
		try {
			ic = new InitialContext();
			System.out.println("in getUserTransaction");
			return (UserTransaction) ic.lookup("java:comp/UserTransaction");
		} catch (NamingException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	
	private void getVCAPCredentials() {
		System.out.println("in getVCAPCredentials");
		
		String VCAP_SERVICES = System.getenv("VCAP_SERVICES");
		System.out.println("VCAP_SERVICES content: " + VCAP_SERVICES);
		
		if (VCAP_SERVICES != null) {
			// parse the VCAP JSON structure
			BasicDBObject obj = (BasicDBObject) JSON.parse(VCAP_SERVICES);
			String thekey = null;
			Set<String> keys = obj.keySet();
			System.out.println("Searching through VCAP keys");
			// Look for the VCAP key that holds the SQLDB information
			for (String eachkey : keys) {
				System.out.println("Key is: " + eachkey);
				// Just in case the service name gets changed
				// to lower case in the future, use toUpperCase
				if (eachkey.toUpperCase().contains("CLEARDB")) {
					thekey = eachkey;
				}
			}
			if (thekey == null) {
				System.out.println("Cannot find any CLEARDB service in VCAP; exit");
				return;
			}
			BasicDBList list = (BasicDBList) obj.get(thekey);
			obj = (BasicDBObject) list.get("0");
			System.out.println("Service found: " + obj.get("name"));
			// parse all the credentials from the vcap env variable
			obj = (BasicDBObject) obj.get("credentials");
			
			String uri = (String) obj.get("jdbcUrl");
			System.out.println("uri = "+uri);
			String username = (String) obj.get("username");
			System.out.println("_dbUser = "+username);
			String password = (String) obj.get("password");
			System.out.println("_dbPW = "+password);
			
			props.put("javax.persistence.jdbc.url", uri);
			props.put("javax.persistence.jdbc.user", username);
			props.put("javax.persistence.jdbc.password", password);
			props.put("javax.persistence.jdbc.driver", "com.mysql.jdbc.Driver");
		} else {
			System.out.println("VCAP_SERVICES is null");
		}
		
		System.out.println("Updated props");
	}
}

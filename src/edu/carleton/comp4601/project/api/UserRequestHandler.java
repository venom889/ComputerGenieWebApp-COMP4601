package edu.carleton.comp4601.project.api;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.JAXBElement;
import javax.ws.rs.core.Response;

import org.joda.time.DateTime;

import edu.carleton.comp4601.project.dao.Review;
import edu.carleton.comp4601.project.dao.User;
import edu.carleton.comp4601.project.datebase.DatabaseManager;
import edu.carleton.comp4601.project.model.GenricServerResponse;
import edu.carleton.comp4601.project.model.UserProfile;

public class UserRequestHandler extends Action {

	public UserRequestHandler(UriInfo uriInfo, Request request, String id) {
		super(uriInfo, request, id);
	}

	private String buildAuthToken(String email, String password, String time) throws UnsupportedEncodingException, NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		md.update(password.getBytes("UTF-8"));
		
		StringBuffer hexString = new StringBuffer();
		byte[] hash = md.digest();

        for (int i = 0; i < hash.length; i++) {
            if ((0xff & hash[i]) < 0x10) {
                hexString.append("0"
                        + Integer.toHexString((0xFF & hash[i])));
            } else {
                hexString.append(Integer.toHexString(0xFF & hash[i]));
            }
        }
 
		return email + "$" + hash + "$" + time;
	}
	
	
	
	/**
	 * Creates a new user account from the user XML posted. If a user is found with the same email/password pair
	 * it will return that users auth token and false
	 * 
	 * @param user
	 * @return
	 */
	@POST
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_XML)
	public GenricServerResponse createNewUserFromXML(JAXBElement<User> user) {
		User u = user.getValue();
		User search = DatabaseManager.getInstance().findUserByPasswordEmail(u.getEmail(), u.getPasswordHash());
		Response res = null;
		String debug = null;
		System.out.println("New user");
		
		if(search != null) {
			res = Response.notAcceptable(null).build();

			// User already Exists send back there auth token and make a login request
			return new GenricServerResponse(res.getStatus(), "", search.getAuthToken(), false);
		}

		String email = u.getEmail();
		String password = u.getPasswordHash();
		u.setLastLoginTime(new DateTime().getMillis());

		try {
			String auth = buildAuthToken(email, password, Long.toString(u.getLastLoginTime()));
			u.setAuthToken(auth);
			debug = "Built Authtoken";
			if(DatabaseManager.getInstance().addNewUser(u)) {
				debug += " - Added user to db";
				res = Response.ok().build();
				return new GenricServerResponse(res.getStatus(), "", u.getAuthToken(), true);
			}
		} catch (UnsupportedEncodingException | NoSuchAlgorithmException e) {
			System.err.println("Exception hashing password");
			debug += " - " + e.toString();
		}

		res = Response.serverError().build();
		return new GenricServerResponse(res.getStatus(), "", "Server Exception" + " - " + debug, false);
	}

	/**
	 * Logins a user. The auth token in this cause doesn't matter (just make it 0).Takes and email
	 * and a hashed password after in the path. Sends back an updated user object or null if they couldn't
	 * be logged in.
	 * 
	 * @param email
	 * @param password
	 * @return
	 */
	@GET
	@Path("/{email}/{password}")
	@Produces(MediaType.APPLICATION_XML)
	public User loginUserFromXML(@PathParam("email") String email, @PathParam("password") String password) {
		User search = DatabaseManager.getInstance().findUserByPasswordEmail(email, password);
		User updateUser = search;
		
		System.out.println("Login user");
		System.out.println(email);
		System.out.println(password);
		if(search == null) {
			System.out.println("Here");
			return null;
		}
				
		try {
			updateUser.setLastLoginTimeFromDate(new DateTime());
			System.out.println("Update User Login: " + updateUser.getLastLoginTime());
			String time = Long.toString(updateUser.getLastLoginTime());
			
			String auth = buildAuthToken(email, password, time);
			updateUser.setAuthToken(auth);
			System.out.println("New Auth Token: " + updateUser.getAuthToken());

			if(DatabaseManager.getInstance().updateUser(updateUser, search.getId())) {
				System.out.println("Returning user");
				return updateUser;
			}
		} catch (UnsupportedEncodingException | NoSuchAlgorithmException e) {
			System.err.println("Exception hashing password");
		}

		return null;
	}
	
	/**
	 * Gets another users profile. Needs the auth token of the user searching and
	 * the id of the user they are looking for.
	 * 
	 * @param id
	 * @return
	 */
	@GET
	@Path("/{id}")
	@Produces(MediaType.APPLICATION_XML)
	public User getUserAsXML(@PathParam("id") String id) {
		
		User search = DatabaseManager.getInstance().findUserByToken(super.authToken);
		
		System.out.println("Get user");
		
		if(search == null) {
			// They are not authorized to use the application
			return null;
		}
		
		User result = DatabaseManager.getInstance().findUser(id);
		return result;
	}
	
	/**
	 * Gets another users profile. Needs the auth token of the user searching and
	 * the id of the user they are looking for.
	 * 
	 * @param id
	 * @return
	 */
	@DELETE
	@Produces(MediaType.APPLICATION_XML)
	public GenricServerResponse deleteUserAsXML() {
		User search = DatabaseManager.getInstance().findUserByToken(super.authToken);
		Response res = null;
		
		System.out.println("Delete user");
		
		if(search == null) {
			// They are not authorized to use the application
			res = Response.status(401).build();
			return new GenricServerResponse(res.getStatus(), "", "Not Authorized", false);
		}
		
		if(DatabaseManager.getInstance().removeUser(search.getId()) != null) {
			res = Response.ok().build();
			return new GenricServerResponse(res.getStatus(), "", "Ok", true);
		}
		
		res = Response.serverError().build();
		return new GenricServerResponse(res.getStatus(), "", "User not found", false);
	}
	
	@POST
	@Path("/update")
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_XML)
	public GenricServerResponse updateUserFromXML(JAXBElement<User> user) {
		
		User newUser = user.getValue();
		User oldUser = DatabaseManager.getInstance().findUser(newUser.getId());
		
		Response res = null;
		
		if(oldUser == null) {
			// They are not authorized to use the application
			res = Response.status(401).build();
			return new GenricServerResponse(res.getStatus(), "", "Not Authorized", false);
		}
		
		newUser.setPasswordHash(oldUser.getPasswordHash());

		System.out.println("Update user");
		
		if(DatabaseManager.getInstance().updateUser(newUser, oldUser.getId())) {
			res = Response.ok().build();
			return new GenricServerResponse(res.getStatus(), "", "Ok", true);
		}
		
		res = Response.serverError().build();
		return new GenricServerResponse(res.getStatus(), "", "User not found", false);
	}
	
	@GET
	@Path("/profile")
	@Produces(MediaType.APPLICATION_XML)
	public UserProfile getProfileForUserAsXML() {
		
		User userSearch = DatabaseManager.getInstance().findUserByToken(super.authToken);

		if(userSearch == null) {
			return null;
		}
		
		ArrayList<Review> results = DatabaseManager.getInstance().getReviewsByUserId(userSearch.getId());
		
		int total = results.size();
		int upvotes = 0;		
		int downvotes = 0;
		
		for(Review r : results) {
			upvotes += r.getUpScore();
			downvotes += r.getDownScore();
		}
		
		return new UserProfile(upvotes,downvotes,total);
	}
	
 }

package com.vrp.resource;

import com.vrp.entity.Customer;
import com.vrp.entity.ShiftDemand;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/customers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CustomerResource {
    
    @GET
    public List<Customer> listAll() {
        return Customer.listAll();
    }
    
    @GET
    @Path("/{id}")
    public Customer get(@PathParam("id") Long id) {
        Customer customer = Customer.findById(id);
        if (customer == null) {
            throw new NotFoundException("Customer not found");
        }
        return customer;
    }
    
    @POST
    @Transactional
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response createFromForm(
            @FormParam("name") String name,
            @FormParam("address") String address,
            @FormParam("latitude") Double latitude,
            @FormParam("longitude") Double longitude) {
        Customer customer = new Customer();
        customer.name = name;
        customer.address = address;
        customer.latitude = latitude;
        customer.longitude = longitude;
        customer.persist();
        return Response.status(Response.Status.CREATED).entity(customer).build();
    }
    
    @POST
    @Transactional
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(Customer customer) {
        customer.persist();
        return Response.status(Response.Status.CREATED).entity(customer).build();
    }
    
    @PUT
    @Path("/{id}")
    @Transactional
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Customer updateFromForm(
            @PathParam("id") Long id,
            @FormParam("name") String name,
            @FormParam("address") String address,
            @FormParam("latitude") Double latitude,
            @FormParam("longitude") Double longitude) {
        Customer customer = Customer.findById(id);
        if (customer == null) {
            throw new NotFoundException("Customer not found");
        }
        customer.name = name;
        customer.address = address;
        customer.latitude = latitude;
        customer.longitude = longitude;
        return customer;
    }
    
    @PUT
    @Path("/{id}")
    @Transactional
    @Consumes(MediaType.APPLICATION_JSON)
    public Customer update(@PathParam("id") Long id, Customer updatedCustomer) {
        Customer customer = Customer.findById(id);
        if (customer == null) {
            throw new NotFoundException("Customer not found");
        }
        customer.name = updatedCustomer.name;
        customer.address = updatedCustomer.address;
        customer.latitude = updatedCustomer.latitude;
        customer.longitude = updatedCustomer.longitude;
        return customer;
    }
    
    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        Customer customer = Customer.findById(id);
        if (customer == null) {
            throw new NotFoundException("Customer not found");
        }
        customer.delete();
        return Response.noContent().build();
    }
    
    @POST
    @Path("/{id}/shifts")
    @Transactional
    public Response addShift(@PathParam("id") Long id, ShiftDemand shift) {
        Customer customer = Customer.findById(id);
        if (customer == null) {
            throw new NotFoundException("Customer not found");
        }
        shift.customer = customer;
        shift.persist();
        return Response.status(Response.Status.CREATED).entity(shift).build();
    }
}

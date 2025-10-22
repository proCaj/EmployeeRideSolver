package com.vrp.resource;

import com.vrp.entity.Customer;
import com.vrp.entity.Employee;
import com.vrp.entity.ShiftDemand;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.DayOfWeek;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/")
public class WebResource {

    @Inject
    Template dashboard;
    
    @Inject
    Template employees;
    
    @Inject
    @io.quarkus.qute.Location("employee-form.html")
    Template employeeForm;
    
    @Inject
    Template customers;
    
    @Inject
    @io.quarkus.qute.Location("customer-form.html")
    Template customerForm;
    
    @Inject
    Template shifts;
    
    @Inject
    @io.quarkus.qute.Location("shift-form.html")
    Template shiftForm;
    
    @Inject
    Template optimize;
    
    @Inject
    Template routes;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance index() {
        long employeeCount = Employee.count();
        long customerCount = Customer.count();
        long shiftCount = ShiftDemand.count();
        
        return dashboard
            .data("title", "Dashboard")
            .data("employeeCount", employeeCount)
            .data("customerCount", customerCount)
            .data("shiftCount", shiftCount);
    }
    
    @GET
    @Path("/employees")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance listEmployees() {
        List<Employee> employeeList = Employee.listAll();
        return employees
            .data("title", "Employees")
            .data("employees", employeeList);
    }
    
    @GET
    @Path("/employees/new")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance newEmployee() {
        Employee employee = new Employee();
        employee.active = true;
        return employeeForm
            .data("title", "New Employee")
            .data("employee", employee)
            .data("isEdit", false);
    }
    
    @GET
    @Path("/employees/{id}/edit")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance editEmployee(@PathParam("id") Long id) {
        Employee employee = Employee.findById(id);
        return employeeForm
            .data("title", "Edit Employee")
            .data("employee", employee)
            .data("isEdit", true);
    }
    
    @GET
    @Path("/optimize")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance optimizePage() {
        long customerCount = Customer.count();
        long driverCount = Employee.count("active = true and employeeType = 'DRIVER'");
        long employeeCount = Employee.count("active = true and employeeType = 'SITE_EMPLOYEE'");
        
        return optimize
            .data("title", "Route Optimization")
            .data("customerCount", customerCount)
            .data("driverCount", driverCount)
            .data("employeeCount", employeeCount);
    }
    
    @GET
    @Path("/routes")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance routesPage() {
        return routes
            .data("title", "Routes")
            .data("routes", List.of());
    }
    
    @GET
    @Path("/customers")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance customersPage() {
        List<Customer> customerList = Customer.listAll();
        return customers
            .data("title", "Customers")
            .data("customers", customerList);
    }
    
    @GET
    @Path("/customers/new")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance newCustomer() {
        Customer customer = new Customer();
        return customerForm
            .data("title", "New Customer")
            .data("customer", customer)
            .data("isEdit", false);
    }
    
    @GET
    @Path("/customers/{id}/edit")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance editCustomer(@PathParam("id") Long id) {
        Customer customer = Customer.findById(id);
        return customerForm
            .data("title", "Edit Customer")
            .data("customer", customer)
            .data("isEdit", true);
    }
    
    @GET
    @Path("/shifts")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance shiftsPage() {
        List<ShiftDemand> shiftList = ShiftDemand.listAll();
        
        Map<DayOfWeek, List<ShiftDemand>> shiftsByDay = new LinkedHashMap<>();
        shiftsByDay.put(DayOfWeek.MONDAY, shiftList.stream().filter(s -> s.dayOfWeek == DayOfWeek.MONDAY).collect(Collectors.toList()));
        shiftsByDay.put(DayOfWeek.TUESDAY, shiftList.stream().filter(s -> s.dayOfWeek == DayOfWeek.TUESDAY).collect(Collectors.toList()));
        shiftsByDay.put(DayOfWeek.WEDNESDAY, shiftList.stream().filter(s -> s.dayOfWeek == DayOfWeek.WEDNESDAY).collect(Collectors.toList()));
        shiftsByDay.put(DayOfWeek.THURSDAY, shiftList.stream().filter(s -> s.dayOfWeek == DayOfWeek.THURSDAY).collect(Collectors.toList()));
        shiftsByDay.put(DayOfWeek.FRIDAY, shiftList.stream().filter(s -> s.dayOfWeek == DayOfWeek.FRIDAY).collect(Collectors.toList()));
        shiftsByDay.put(DayOfWeek.SATURDAY, shiftList.stream().filter(s -> s.dayOfWeek == DayOfWeek.SATURDAY).collect(Collectors.toList()));
        shiftsByDay.put(DayOfWeek.SUNDAY, shiftList.stream().filter(s -> s.dayOfWeek == DayOfWeek.SUNDAY).collect(Collectors.toList()));
        
        shiftsByDay.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        
        return shifts
            .data("title", "Shifts")
            .data("shifts", shiftList)
            .data("shiftsByDay", shiftsByDay);
    }
    
    @GET
    @Path("/shifts/new")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance newShift() {
        ShiftDemand shift = new ShiftDemand();
        shift.active = true;
        shift.requiresReturnTrip = false;
        shift.requiredEmployees = 1;
        List<Customer> customerList = Customer.listAll();
        return shiftForm
            .data("title", "New Shift")
            .data("shift", shift)
            .data("customers", customerList)
            .data("employees", List.of())
            .data("isEdit", false);
    }
    
    @GET
    @Path("/shifts/{id}/edit")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance editShift(@PathParam("id") Long id) {
        ShiftDemand shift = ShiftDemand.findById(id);
        List<Customer> customerList = Customer.listAll();
        List<Employee> employeeList = Employee.listAll();
        return shiftForm
            .data("title", "Edit Shift")
            .data("shift", shift)
            .data("customers", customerList)
            .data("employees", employeeList)
            .data("isEdit", true);
    }
    
    @GET
    @Path("/shifts/{id}")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance shiftDetail(@PathParam("id") Long id) {
        ShiftDemand shift = ShiftDemand.findById(id);
        List<Employee> allEmployees = Employee.listAll();
        return shiftForm
            .data("title", "Shift Details")
            .data("shift", shift)
            .data("customers", Customer.listAll())
            .data("employees", allEmployees)
            .data("isEdit", true);
    }
}

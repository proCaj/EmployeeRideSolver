package com.vrp.resource;

import com.vrp.domain.Driver;
import com.vrp.domain.Event;
import com.vrp.domain.VrpSolution;
import com.vrp.entity.Customer;
import com.vrp.entity.Employee;
import com.vrp.entity.ShiftDemand;
import com.vrp.service.SolverService;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.DayOfWeek;
import java.util.*;
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
    
    @Inject
    SolverService solverService;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance index() {
        long employeeCount = Employee.count();
        long customerCount = Customer.count();
        long shiftCount = ShiftDemand.count();
        
        return dashboard
            .data("title", "Dashboard")
            .data("activeNav", "dashboard")
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
            .data("activeNav", "employees")
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
            .data("activeNav", "employees")
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
            .data("activeNav", "employees")
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
            .data("activeNav", "optimize")
            .data("customerCount", customerCount)
            .data("driverCount", driverCount)
            .data("employeeCount", employeeCount);
    }
    
    @GET
    @Path("/routes")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance routesPage() {
        UUID currentJobId = solverService.getCurrentJobId();
        List<Map<String, Object>> routeList = new ArrayList<>();
        
        if (currentJobId != null) {
            VrpSolution solution = solverService.getBestSolution(currentJobId);
            
            if (solution != null && solution.getDrivers() != null) {
                Map<Driver, List<Event>> eventsByDriver = new HashMap<>();
                for (Event event : solution.getEvents()) {
                    if (event.getDriver() != null) {
                        eventsByDriver.computeIfAbsent(event.getDriver(), k -> new ArrayList<>()).add(event);
                    }
                }
                
                int routeNumber = 1;
                for (Driver driver : solution.getDrivers()) {
                    List<Event> events = eventsByDriver.get(driver);
                    if (events == null || events.isEmpty()) continue;
                    
                    events.sort((e1, e2) -> {
                        if (e1.getArrivalTime() == null) return 1;
                        if (e2.getArrivalTime() == null) return -1;
                        return e1.getArrivalTime().compareTo(e2.getArrivalTime());
                    });
                    
                    long totalDistance = 0;
                    for (Event event : events) {
                        totalDistance += event.getDistance();
                    }
                    
                    Map<String, Object> route = new HashMap<>();
                    route.put("driver", driver);
                    route.put("routeNumber", routeNumber++);
                    route.put("events", events);
                    route.put("totalDistance", String.format("%.1f", totalDistance / 1000.0));
                    route.put("totalDuration", formatDuration(events));
                    
                    routeList.add(route);
                }
            }
        }
        
        return routes
            .data("title", "Routes")
            .data("activeNav", "routes")
            .data("routes", routeList);
    }
    
    private String formatDuration(List<Event> events) {
        if (events.isEmpty()) return "-";
        Event first = events.get(0);
        Event last = events.get(events.size() - 1);
        if (first.getArrivalTime() == null || last.getDepartureTime() == null) return "-";
        long minutes = java.time.Duration.between(first.getArrivalTime(), last.getDepartureTime()).toMinutes();
        if (minutes < 60) return minutes + " min";
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }
    
    @GET
    @Path("/customers")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance customersPage() {
        List<Customer> customerList = Customer.listAll();
        return customers
            .data("title", "Customers")
            .data("activeNav", "customers")
            .data("customers", customerList);
    }
    
    @GET
    @Path("/customers/new")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance newCustomer() {
        Customer customer = new Customer();
        return customerForm
            .data("title", "New Customer")
            .data("activeNav", "customers")
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
            .data("activeNav", "customers")
            .data("customer", customer)
            .data("isEdit", true);
    }
    
    @GET
    @Path("/shifts")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance shiftsPage() {
        List<ShiftDemand> shiftList = ShiftDemand.listAll();
        List<Employee> siteEmployees = Employee.list("employeeType = ?1 and active = true", 
            com.vrp.entity.EmployeeType.SITE_EMPLOYEE);
        
        Map<DayOfWeek, List<ShiftDemand>> shiftsByDay = shiftList.stream()
            .collect(Collectors.groupingBy(s -> s.dayOfWeek, LinkedHashMap::new, Collectors.toList()));
        
        shiftsByDay.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        
        return shifts
            .data("title", "Shifts")
            .data("activeNav", "shifts")
            .data("shifts", shiftList)
            .data("shiftsByDay", shiftsByDay)
            .data("siteEmployees", siteEmployees);
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
            .data("activeNav", "shifts")
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
            .data("activeNav", "shifts")
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
            .data("activeNav", "shifts")
            .data("shift", shift)
            .data("customers", Customer.listAll())
            .data("employees", allEmployees)
            .data("isEdit", true);
    }
}

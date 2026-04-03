# Maintenance Module Enhancement: High-Level Business Perspective

To enhance your **Maintenance Module**, you need to bridge the gap between "something is scheduled" and "knowing exactly what is happening in the shop in real-time." Based on your current screenshots, the flow is a bit static. 

Here is a high-level business perspective on the enhancements for monitoring your fleet from the back office using your Kotlin stack.

---

## 1. The "Live Shop Floor" Monitoring
Currently, your status is either "Scheduled" or "In Progress." From a business standpoint, you need to monitor the **stages of labor** to identify bottlenecks.

* **Sub-Status Tracking:** Instead of just "In Progress," track `Awaiting Parts`, `Under Repair`, and `Quality Control`. 
* **Mechanic Assignment:** Monitor *who* is working on the vehicle. This allows you to track labor efficiency and accountability directly from the back office.
* **Real-time Frontend Update:** Since you are using Kotlin (likely with a framework like Ktor or Spring Boot), implement **WebSockets**. When a mechanic clicks "Start Work" on their side, your back-office dashboard should pulse or update the row color instantly without a page refresh.

## 2. Telemetry-Driven Maintenance Triggers
Instead of manual scheduling, your backend should act as a "watchdog" over your vehicle data.

* **Odometer Sync:** Your "Vehicle Details" shows a manual odometer update. The backend should monitor mileage via GPS/Telemetry API. 
* **Predictive Alerts:** If a Toyota Vios (like ASB-111) is averaging 500km/week, the backend should calculate the date it will hit its next 5,000km service and auto-create a "Draft" maintenance task.
* **Business Value:** This moves you from *reactive* maintenance (fixing what's broken) to *proactive* fleet health.

## 3. The "Paper Trail" & Digital Documentation
To monitor actions taken, you need more than just a description box. You need evidence.

* **Inspection Checklists:** Before a job moves to "Completed," the backend should require a digital checklist (e.g., Oil level checked, Tire pressure set, Brake pads inspected).
* **Photo Evidence:** Allow the frontend to upload "Before" and "After" photos stored in your backend (S3/Cloud storage). In the back office, clicking a completed job should show a gallery of the work done.
* **Parts Consumption:** Link the maintenance job to a "Parts Inventory." When a mechanic uses a filter or oil, it should deduct from your stock and update the `Estimated Cost` to `Actual Cost` in real-time.

## 4. Financial & Performance Analytics
Since you have an **Accounting** module, the bridge between Maintenance and Finance is critical.

* **Downtime Costing:** Monitor how many hours/days a vehicle is "Out of Service." 
    > **Business Formula:** $$Downtime\ Cost = (Daily\ Revenue\ Potential \times Days\ in\ Shop) + Repair\ Cost$$
* **Vendor Performance:** If you outsource repairs, monitor the "Estimated vs. Actual" turnaround time and cost to see which shops are overcharging or taking too long.

---

### Backend vs. Frontend Action Flow (Kotlin)

| Action Stage | Frontend (Kotlin/Compose/React) | Backend (Kotlin/Spring/Ktor) |
| :--- | :--- | :--- |
| **Trigger** | High-mileage alert or manual schedule. | Service sends notification to Fleet Manager. |
| **Assignment** | Manager assigns to a specific Mechanic/Bay. | Updates `maintenance_logs` table with `mechanic_id`. |
| **Execution** | Mechanic taps **"Start Work"**. | Timestamp recorded; status changed to `IN_PROGRESS`. |
| **Verification** | Checklist completed + Photos uploaded. | Validates all required fields before allowing status change. |
| **Close-out** | Manager reviews and hits **"Approve"**. | Final costs pushed to **Accounting Module** (Invoice/Expense). |

### Summary of Key Back-Office Metrics to Monitor:
1.  **MTTR (Mean Time To Repair):** How fast are we fixing cars?
2.  **Maintenance Compliance:** What % of the fleet is overdue for service?
3.  **Recurring Issues:** Is the same Toyota Vios coming back for the same engine issue every month? (Flag for "Lemon" or poor repair quality).

By implementing these, your back office moves from a simple "list of tasks" to a **command center** that optimizes vehicle uptime and reduces operational costs.
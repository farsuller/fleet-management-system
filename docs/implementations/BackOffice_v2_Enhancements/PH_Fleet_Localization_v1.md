# Fleet Management Platform: Philippines Localization Requirements (2026)

## 1. Fleet Manager Dashboard (Business Level)
*The "Command Center" for local operational efficiency and risk mitigation.*

### **A. Traffic & Compliance (MMDA/LTO)**
* **MMDA Number Coding Monitor:**
    * **Visual:** A daily status bar showing which plate endings are restricted (e.g., "Monday: 1 & 2 restricted").
    * **Logic:** Integrated alerts if a "Coded" vehicle is dispatched or moving during peak hours (7AM–10AM / 5PM–8PM).
    * **City-Specific Toggles:** Specialized alerts for Makati City (7AM–7PM strictly) vs. cities with "Window Hours."
* **LTO/LTFRB Legal Health Score:**
    * **Renewal Calendar:** Automated schedule based on the last digit of the plate (Monthly) and the 2nd to last digit (Weekly).
    * **Franchise Tracker:** Countdown for "Certificate of Public Convenience" (CPC) and "Provisional Authority" (PA) expirations.

### **B. Logistics & Infrastructure**
* **Dual RFID Wallet Tracking:**
    * Live balance monitoring for **Autosweep (SMC)** and **Easytrip (MPTC)**.
    * Low-balance threshold alerts (e.g., Flag vehicle if balance < ₱500 before a provincial trip).
* **PAGASA Weather Integration:**
    * Real-time "Typhoon Signal" overlay on the vehicle tracking map.
    * Geofencing alerts for flood-prone "Red Zones" in Metro Manila (e.g., España, Araneta Ave, Malabon).

### **C. Performance Metrics**
* **Fleet Utilization Rate:** Percentage of the fleet actively generating revenue vs. idle/maintenance.
* **Traffic Leakage Analytics:** Measuring "Fuel Burned" during idling vs. actual kilometers traveled to identify high-cost routes.

---

## 2. Finance & Accounting Module
*Localized for BIR compliance, local e-wallets, and Philippine tax laws.*

### **A. Localized Accounting (BIR-Ready)**
* **E-Wallet Reconciliation:**
    * Dedicated ledger for **GCash, Maya, and GrabPay** collections.
    * Automatic deduction of 1%–2% platform transaction fees to reflect net revenue.
* **VAT & EOPT Compliance:**
    * **2024 EOPT Act Support:** Transition from "Official Receipts" to "VAT Invoices" for all rental services.
    * **Withholding Tax (EWT) Generator:** Automatic calculation of 1%, 2%, or 5% EWT based on vendor type.
* **Government Benefit Tracking:**
    * Dashboard for **SSS, PhilHealth, and Pag-IBIG** employer contributions.
    * Alerts for administrative overpayments or delinquency risks.

### **B. Financial Reports**
* **Unit Economics (Profit per Plate):** `Revenue - (Fuel + Tolls + Maintenance + Driver Commission + Tax)`.
* **Aging Accounts Receivable (AR):** Categorized by 0-30, 31-60, 61-90, and 90+ days (Critical for corporate accounts/3PL partners).
* **Cash Flow Forecast:** 90-day projection to prepare for "13th Month Pay," annual insurance premiums, and LTO bulk renewals.
* **Tollway Expenditure Report:** Comparison of NLEX vs. SLEX vs. Skyway costs per route to optimize dispatching.

---

## 3. Recommended Tech Stack Integrations for PH
* **Maps:** Google Maps API with "Traffic Layer" (Waze integration for drivers).
* **Payments:** Xendit or Maya Business for local gateway support.
* **SMS:** Semaphore or Twilio for local "Low Balance" or "Coding Alert" SMS notifications.

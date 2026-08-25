# EV Charge Book Database Design

## Core Entities

### Vehicle

用于记录车辆信息。

Fields:

- id
- brand
- model
- battery_capacity
- range_km
- purchase_date

Example:

Zero Run C16 2026

Battery: LFP 67.7kWh

---

### ChargingRecord

记录每一次充电。

Fields:

- id
- vehicle_id
- charge_time
- start_soc
- end_soc
- energy_kwh
- cost
- charger_type
- location
- remark

---

### DrivingRecord

记录行驶数据。

Fields:

- id
- vehicle_id
- date
- mileage
- average_consumption

---

## Statistics

Calculated metrics:

- monthly cost
- cost per kilometer
- average energy consumption
- charging frequency

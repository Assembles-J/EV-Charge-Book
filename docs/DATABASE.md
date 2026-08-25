# EV Charge Book Database Design

Version: v1.1.0

## Design Principle

Local First.

Android 本地 Room 作为第一数据源，未来支持云端同步。

## Core Entities

## Vehicle

车辆基础信息。

Fields:

- id
- brand
- model
- battery_capacity_kwh
- range_km
- battery_type
- purchase_date

Example:

Zero Run C16 2026

Battery: LFP 67.7kWh

---

## ChargingRecord

记录每一次充电行为。

Fields:

- id
- vehicle_id
- charge_time
- start_soc
- end_soc
- energy_kwh
- cost
- price_per_kwh
- charger_type
- location
- remark

Calculated:

- charging efficiency
- average charging cost

---

## DrivingRecord

记录车辆行驶情况。

Fields:

- id
- vehicle_id
- date
- mileage
- average_consumption
- weather
- road_type

---

## BatteryHealth

电池健康趋势模型。

Fields:

- id
- vehicle_id
- record_date
- charge_cycles
- fast_charge_ratio
- health_score

---

## CostSummary

统计数据。

Fields:

- id
- vehicle_id
- month
- total_cost
- total_energy
- total_mileage
- cost_per_km

---

## Future Cloud Mapping

User

 |

Vehicle

 |

ChargingRecord

 |

Analysis

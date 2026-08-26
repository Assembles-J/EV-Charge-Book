# EV Charge Book Feature Matrix

Version: v1.1.0
更新时间: 2026-08-26

## v0.1 - Local Charging Book

### Vehicle
- [x] Vehicle profile persistence
- [x] Vehicle edit flow

### Charging
- [x] Add charging record
- [x] Charging history
- [x] Delete charging record
- [ ] Edit charging record
- [ ] Date/time selection
- [ ] Charger type / remark
- [ ] Delete confirmation / save feedback

### Dashboard / Stats
- [x] Monthly cost
- [x] Monthly energy
- [x] Average electricity price
- [x] Charging count
- [x] Total cost / energy

### Delivery
- [ ] Android CI Green
- [ ] Debug APK Artifact
- [ ] First signed production APK

---

## v0.2 - Vehicle & Trip Foundation

### Multi Vehicle
- [ ] Add multiple vehicles
- [ ] Current/default vehicle switcher
- [ ] Vehicle archive
- [ ] Dashboard / Records / Stats scoped by vehicle

### Vehicle Catalog
- [ ] Local vehicle catalog seed
- [ ] Brand / series / model-year / trim search
- [ ] Catalog parameter confirmation/override
- [ ] Custom vehicle fallback

### Location / Map
- [ ] Get current location
- [ ] Charging record location capture
- [ ] MapLibre map adapter
- [ ] Route polyline display

### Trip Tracking
- [ ] Manual start/stop trip
- [ ] Location foreground service
- [ ] Record lat/lng/altitude/speed/accuracy/time
- [ ] Distance / duration / avg speed / max speed
- [ ] Trip history and detail
- [ ] Trip bound to vehicle

---

## v0.3 - Analytics

- [ ] Cost / energy trends
- [ ] Fast/slow charge ratio
- [ ] Monthly comparison
- [ ] Cost per 100 km
- [ ] Trip / charging relationship
- [ ] Altitude / speed / efficiency analysis where data is reliable

---

## v0.4 - Cloud & Catalog Sync

- [ ] Account system
- [ ] Charging / vehicle / trip sync
- [ ] Backup
- [ ] Vehicle catalog update service

---

## v0.5 - Smart Input

- [ ] OCR charging receipt/order
- [ ] Smart field completion
- [ ] Frequent station / tariff reuse

---

## v1.0 - AI Assistant

- [ ] Charging advice
- [ ] Usage and cost analysis
- [ ] Driving efficiency suggestions
- [ ] Explain anomalies from real user data

## Change Log

### v1.1.0
- Reconciled actual v0.1 progress
- Added v0.2 multi-vehicle / catalog / location / trip scope
- Shifted analytics, cloud and smart input to later phases

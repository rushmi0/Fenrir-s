# fenrirs

fenrirs เป็นหนึ่งในซอฟต์แวร์ relay มากมายที่ออกแบบมาเพื่อตอบสนองการทำงานของ [Nostr Protocol](https://nostr.com/)

มันถูกพัฒนาขึ้นเพื่อการใช้งานส่วนตัวเหมาะสมมากๆที่จะใช้ในกลุ่มเพื่อน
เพียงกำหนดค่านโยบายตามต้องการและติดตั้งได้อย่างง่ายดาย ที่สำคัญต้องติดตั้ง
Docker ให้เสร็จเรียบร้อยก่อนนะ

# คุณสมบัติที่รองรับ (NIPs)

- ✅ NIP-01 Basic protocol flow
- ✅ NIP-02 Follow List
- ✅ NIP-04 Encrypted Direct Message
- ✅ NIP-09 Event Deletion
- ✅ NIP-11 Relay Information
- ✅ NIP-13 Proof of Work
- ⬜ NIP-15 Marketplace
- ✅ NIP-28 Public Chat
- ⬜ NIP-40 Expiration Timestamp
- ⬜ NIP-42 Authentication of clients to relays
- ✅ NIP-50 Search Capability

# การกำหนดค่า

### กำหนดค่านโยบายของ relay

ไฟล์กำหนดค่า `src/main/resources/application.toml`

| คีย์การกำหนดค่า                                     | คำอธิบาย                                                                       | ค่าเริ่มต้น | ลำดับความสำคัญ |
|-----------------------------------------------------|--------------------------------------------------------------------------------|-------------|----------------|
| nostr.relay.policy.all-pass                         | อนุญาตรับ Event จากทุกคนในจักรวาล                                              | true        | รองลงมา        |
| nostr.relay.policy.follows-pass                     | อนุญาตรับ Event จากเฉพาะคนที่เจ้าของ relay กำลังติดตาม (NIP-02)                | false       | สูง            |
| nostr.relay.policy.proof-of-work.enabled            | รับหรือไม่รับ Event ที่มีค่าความยากต่ำกว่าที่กำหนดหรือไม่มีการทำ proof of work | false       | สูง            |
| nostr.relay.policy.proof-of-work.difficulty-minimum | ค่าความยากขั้นต่ำสุดที่ต้องการสำหรับ proof of work                             | 32          | -              |

> **_ค่าความยากในระดับ 32 ค่อนข้างโหดพอสมควร ถ้าใจดีหน่อยลดลงมาสัก 23 ก็ไม่แย่_**

### ตัวเลือกกำหนดค่าบริการของ relay (ตัวเลือก)

| คีย์การกำหนดค่า                     | คำอธิบาย                                                                                                   | ค่าเริ่มต้น |
|-------------------------------------|------------------------------------------------------------------------------------------------------------|-------------|
| nostr.relay.database.backup.enabled | ทำการดึงข้อมูลเจ้าของ relay ที่กำลังติดตาม (NIP-02) จากรายการ relay ต่างๆที่กำหนด เมื่อ fenrirs เริ่มทำงาน | false       |
| nostr.relay.database.backup.sync    | รายการ relay ที่ fenrirs จะทำการดึงข้อมูลมา                                                                | -           |

### กำหนดค่ารายละเอียด relay (ตัวเลือก)

| คีย์การกำหนดค่า              | คำอธิบาย                         |
|------------------------------|----------------------------------|
| nostr.relay.info.name        | ชื่อของ relay                    |
| nostr.relay.info.description | คำอธิบายเกี่ยวกับ relay          |
| nostr.relay.info.npub        | npub เจ้าของ relay               |
| nostr.relay.info.contact     | ที่อยู่อีเมลล์ที่สามารถติดต่อได้ |

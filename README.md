# Fenrir-s

**Fenrir-s** เป็น Relay ปฏิบัติตามข้อกำหนดของ [Nostr Protocol](https://github.com/nostr-protocol/nips)

โดยมุ่งเน้นสำหรับการใช้งานส่วนตัวหรือในกลุ่มเพื่อนๆ สามารถกำหนดค่านโยบาย Relay ตามความต้องการ และสามารถติดตั้งได้ง่ายๆ
โดยต้องติดตั้ง [Docker](https://www.docker.com/products/docker-desktop/) ให้เสร็จเรียบร้อยก่อนนะครับ

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

# การกำหนดค่า Relay

### 1. กำหนดค่าข้อมูลรายละเอียดของ Relay เบื้องต้น

เป็นเหมือนกับโปรไฟล์ Relay ของเราครับ

ไฟล์กำหนดค่าอยู่ที่ [`src/main/resources/application.toml`](src/main/resources/application.toml)

```toml
[nostr.relay.info]
name = "relay rushmi0"
description = "นึกแล้ว มึงต้องอ่าน"
npub = "npub1ujevvncwfe22hv6d2cjv6pqwqhkvwlcvge7vgm3vcn2max9tu03sgze8ry"
contact = "rushmi0@getalby.com"
```

| ชื่อ                         | คำอธิบาย                         |
|------------------------------|----------------------------------|
| nostr.relay.info.name        | ชื่อของ Relay                    |
| nostr.relay.info.description | คำอธิบายเกี่ยวกับ Relay          |
| nostr.relay.info.npub        | npub เจ้าของ Relay               |
| nostr.relay.info.contact     | ที่อยู่อีเมลล์ที่สามารถติดต่อได้ |

### 2. กำหนดนโยบาย

หากไม่ได้กำหนดค่า Relay ใดๆ ค่าเริ่มต้นจะเป็น public Relay ที่เปิดให้ทุกคนใช้งานได้

```toml
[nostr.relay.policy]
all-pass = true
follows-pass = false

[nostr.relay.policy.proof-of-work]
enabled = false
difficulty-minimum = 32
```

| ชื่อ                                                | คำอธิบาย                                                                       | ค่าเริ่มต้น | ลำดับความสำคัญ |
|-----------------------------------------------------|--------------------------------------------------------------------------------|-------------|----------------|
| nostr.relay.policy.all-pass                         | รับ Event จากทุกคนในจักรวาล                                                    | true        | รองลงมา        |
| nostr.relay.policy.follows-pass                     | อนุญาตรับ Event จากเฉพาะคนที่เจ้าของ Relay กำลังติดตาม (NIP-02)                | false       | สูง            |
| nostr.relay.policy.proof-of-work.enabled            | รับหรือไม่รับ Event ที่มีค่าความยากต่ำกว่าที่กำหนดหรือไม่มีการทำ proof of work | false       | สูง            |
| nostr.relay.policy.proof-of-work.difficulty-minimum | ค่าความยากขั้นต่ำสุดที่ต้องการสำหรับ proof of work                             | 32          | -              |

> _ค่าความยากในระดับ 32 ค่อนข้างโหดพอสมควร ถ้าใจดีหน่อยลดลงมาสัก 23 ก็ไม่แย่_

### 3. ตัวเลือกกำหนดค่าบริการของพิเศษของ Relay

| ชื่อ                                | คำอธิบาย                                                                                                | ค่าเริ่มต้น |
|-------------------------------------|---------------------------------------------------------------------------------------------------------|-------------|
| nostr.relay.database.backup.enabled | ทำการดึงข้อมูลเจ้าของ Relay ที่กำลังติดตาม (NIP-02) จากรายการ Relay ต่างๆที่กำหนดเมื่อ Relay เริ่มทำงาน | false       |
| nostr.relay.database.backup.sync    | รายการ Relay อื่นๆ ที่จะการดึงข้อมูลมา                                                                  | -           |

<br>

# ขั้นตอนการติดตั้ง

1. เริ่มต้นด้วยการเปิด terminal ขั้นมาก่อนโหลดตัวโปรเจ็กต์จาก GitHub ลงเครื่องของเราก่อน
   และเขาไปข้างในไดเร็กทอรีนั้นโดยใช้คำสั่งชุดนี้

```shell
git clone https://github.com/rushmi0/Fenrir-s.git
cd Fenrir-s
````

### การใช้งานสำหรับ Docker

2. เมื่อเรามาขั้นตอนนี้เรารันคำสั่งชุดนี้ได้เลย แต่จะต้อนแน่ใจว่าเปิด Docker แล้วนะครับ

```shell
docker compose up -d relay-db relay-cache relay-app
```

ผมได้จัดเตรียม cloudflare tunnel ไว้ให้แล้วในส่วน [docker-compose.yml](docker-compose.yml) เพียงแค่นำ token ไปใส่นะครับ


# SMS PDU formatting — a technical reference

This is a reference explainer for how SMS messages are encoded at the
protocol level (the "PDU" — Protocol Data Unit), per 3GPP TS 23.040. It is
documentation only: the app itself sends ordinary messages through the public
`SmsManager` API and does **not** construct raw PDUs.

## Where the fields live

A submitted SMS (SMS-SUBMIT, phone → network) is a byte string. The parts
relevant to message *type* are:

| Field | Meaning |
|-------|---------|
| SCA | Service Centre Address (the SMSC number) |
| PDU-Type | Message-type and flags octet (MTI, RD, VPF, SRR, UDHI, RP) |
| MR | Message Reference |
| DA | Destination Address |
| **TP-PID** | Protocol Identifier |
| **TP-DCS** | Data Coding Scheme |
| VP | Validity Period |
| UDL | User-Data Length |
| UD | User Data (the message text, optionally with a header) |

The two bytes that decide "what kind" of SMS this is are **TP-PID** and
**TP-DCS**.

## TP-DCS — Data Coding Scheme and message class

The DCS octet selects the character encoding and, optionally, a *message
class*:

- Bits 3–2 — alphabet: `00` = GSM 7-bit, `01` = 8-bit data, `10` = UCS-2 (16-bit).
- Bit 4 — set to `1` means "a message class is present in bits 1–0".
- Bits 1–0 — the class, when bit 4 is set:
  - **Class 0** — "flash" SMS: shown immediately on screen, not automatically
    stored. Highly *visible* to the recipient.
  - Class 1 — ME-specific (normal; stored on the device).
  - Class 2 — (U)SIM-specific.
  - Class 3 — TE-specific (forwarded to external equipment).

Example: `DCS = 0x10` → GSM 7-bit alphabet, message class present, class 0.

## TP-PID — Protocol Identifier

The PID octet normally tells the SMSC what higher-layer protocol the message
belongs to. A PID of `0x40` ("Type 0") denotes a message the network should
acknowledge but which carries no user-visible content — historically the basis
of "silent" pings. Because its purpose is to reach a handset *without the
holder's knowledge*, it is a surveillance primitive, and this project does not
implement it.

## User Data Header (concatenation)

Long messages are split into segments of 153 GSM-7 characters (or 67 UCS-2),
and each segment carries a **UDH** with a concatenation IE (reference, total
count, sequence index) so the receiver can reassemble them. The app relies on
`SmsManager.divideMessage()` / `sendMultipartTextMessage()` to build these
headers; the "part(s)" counter in the UI mirrors that split.

## Why the app cannot set TP-PID or force Class 0

Android's public `SmsManager` API accepts a destination, an optional SMSC, and
the message text. It does **not** expose the PDU-Type, TP-PID, or TP-DCS
octets, so an ordinary app cannot mark a message as Type-0/silent or force
Class-0 flash through it. Tools that do so operate below this API — writing
raw PDUs to the radio interface layer (RIL), which requires root or a
privileged, platform-signed role. That lower-level access is exactly what turns
SMS sending into a covert/surveillance capability, and it is intentionally out
of scope here.

## References

- 3GPP TS 23.040 — Technical realization of the Short Message Service (SMS)
- 3GPP TS 23.038 — Alphabets and language-specific information (DCS details)

# KakaoBot Server API Documentation

## Endpoint
- **URL**: `https://kakaobot-server.vercel.app/api/v1/process-message`
- **Method**: POST
- **Content-Type**: application/json

## Request Schema
```json
{
  "id": "string (required)",
  "message": "string (required)",
  "isGroup": "boolean (optional)",
  "groupName": "string|null (optional)", 
  "sender": "string (optional)",
  "timestamp": "number (optional)",
  "deviceId": "string (optional)"
}
```

## Response Schema
```json
{
  "id": "string",
  "success": "boolean",
  "reply": "string|null",
  "processingTime": "number (milliseconds)",
  "error": "ErrorInfo|null"
}
```

## ErrorInfo Schema
```json
{
  "code": "string",
  "message": "string",
  "retryAfter": "number (optional)"
}
```

## Processing Logic
- **@GPT Detection**: If message contains "@GPT" → responds with `"Hello, I'm GPT!"`
- **No Match**: If message doesn't contain "@GPT" → returns `reply: null`
- **Validation**: Requires `id` and `message` fields

## Response Examples

### Successful Response (with @GPT)
```json
{
  "id": "test1",
  "success": true,
  "reply": "Hello, I'm GPT!",
  "processingTime": 3,
  "error": null
}
```

### Successful Response (no @GPT)
```json
{
  "id": "test2", 
  "success": true,
  "reply": null,
  "processingTime": 2,
  "error": null
}
```

### Error Response (validation failed)
```json
{
  "id": "test3",
  "success": false,
  "reply": null,
  "processingTime": 0,
  "error": {
    "code": "INVALID_REQUEST",
    "message": "Missing required fields: id and message are required"
  }
}
```

## HTTP Status Codes
- **200**: Success (both reply and no-reply cases)
- **400**: Bad Request (validation errors)
- **500**: Internal Server Error

## Error Codes
- `INVALID_REQUEST`: Missing required fields
- `INTERNAL_ERROR`: Server processing error

## Testing Examples
```bash
# Test with @GPT trigger
curl -X POST https://kakaobot-server.vercel.app/api/v1/process-message \
  -H "Content-Type: application/json" \
  -d '{"id":"test1","message":"Hello @GPT!"}'

# Test without @GPT trigger  
curl -X POST https://kakaobot-server.vercel.app/api/v1/process-message \
  -H "Content-Type: application/json" \
  -d '{"id":"test2","message":"Regular message"}'
```

## Client Implementation Notes
- `reply: null` means bot chose not to respond (not an error)
- Always check `success` field before processing `reply`
- `processingTime` tracks actual server processing duration
- Handle network errors separately from API errors
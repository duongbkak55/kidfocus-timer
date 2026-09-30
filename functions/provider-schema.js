// Keep the provider's constrained-decoding grammar small. The server validators
// remain authoritative for ranges, formats, lengths and cross-field rules.
function providerSchema(schema) {
  if (schema === null || typeof schema !== "object") return schema;
  const result = {};
  for (const [key, value] of Object.entries(schema)) {
    if (key === "properties") {
      result.properties = Object.fromEntries(Object.entries(value).map(([name, child]) => [name, providerSchema(child)]));
    } else if (key === "items" || key === "additionalProperties" && typeof value === "object") {
      result[key] = providerSchema(value);
    } else if (key === "anyOf") {
      result.anyOf = value.map(providerSchema);
    } else if (["type", "required", "enum", "additionalProperties"].includes(key)) {
      result[key] = value;
    } else if (key === "const") {
      result.enum = [value];
    }
  }
  return result;
}
module.exports = {providerSchema};

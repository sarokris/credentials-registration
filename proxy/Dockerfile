FROM envoyproxy/envoy:v1.28-latest

# Copy envoy configuration into the image
COPY envoy.yaml /etc/envoy/envoy.yaml

# Expose the port Envoy listens on
EXPOSE 10000

# Run envoy with the provided configuration
CMD ["/usr/local/bin/envoy", "-c", "/etc/envoy/envoy.yaml", "--log-level", "info"]

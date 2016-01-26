FROM vertx/vertx3

ENV VERTICLE_NAME com.jacohend.rxvertrest.rxVertRest
ENV VERTICLE_FILE build/libs/com.jacohend.rxvertrest.rxVertRest-0.0.1-SNAPSHOT-fat.jar

# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles

EXPOSE 8080

# Copy your verticle to the container
COPY $VERTICLE_FILE $VERTICLE_HOME/

# Launch the verticle
WORKDIR $VERTICLE_HOME
ENTRYPOINT ["sh", "-c"]
CMD ["vertx run $VERTICLE_NAME -cp $VERTICLE_HOME/*"]

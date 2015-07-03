package controllers;

import akka.actor.*;
import akka.dispatch.Futures;
import akka.dispatch.Mapper;
import akka.util.Timeout;
import akkaSystem.CreatorActor;
import akkaSystem.LookupActor;
import akkaSystem.MyActor;
import com.google.inject.Inject;
import model.ActorName;
import play.data.Form;
import play.libs.F.Function;
import play.libs.F.Promise;
import play.mvc.Controller;
import play.mvc.Result;
import play.twirl.api.Content;
import scala.concurrent.Await;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;
import views.html.index;
import views.html.multiple;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static akka.pattern.Patterns.ask;


public class Application extends Controller {

    private final FiniteDuration threeSeconds = Duration.create(3, "seconds");
    private final Timeout timeout = new Timeout(threeSeconds);
    private ActorSystem akkaSystem;
    private int nbActors = 3;

    private String address = "akka.tcp://remoteSystem@127.0.0.1:4242";


    @Inject
    public Application(ActorSystem akkaSystem) {
        this.akkaSystem = akkaSystem;

    }

    public Result index() {
        return ok((Content) index.render("en attente"));
    }

    private ActorRef getActor0() throws Exception {
        ActorRef actor0 = null;
        try {
            final ActorSelection as = akkaSystem.actorSelection(address + "/user/creatorActor/actor0");
            final Future<ActorRef> actorRefFuture = as.resolveOne(timeout);
            actor0 = Await.result(actorRefFuture, threeSeconds);
        } catch (Exception e) {
            throw e;
        }
        return actor0;
    }

    private ArrayList<ActorRef> getActors() throws Exception {
        ArrayList<ActorRef> allActors;
        final Inbox inbox = Inbox.create(akkaSystem);
        ActorRef lookupActor;
        try {
            final ActorSelection as = akkaSystem.actorSelection(address + "/user/lookupActor");
            final Future<ActorRef> actorRefFuture = as.resolveOne(timeout);
            lookupActor = Await.result(actorRefFuture, threeSeconds);
        } catch (Exception e) {
            System.err.println("getActors (resolving lookupactor): " + e.getMessage());
            throw e;
        }
        try {
            inbox.send(lookupActor, new LookupActor.GetAllActors(Duration.create(1, TimeUnit.SECONDS)));
            LookupActor.AllActors receive = (LookupActor.AllActors) inbox.receive(threeSeconds);
            allActors = receive.getAllActors();
        } catch (Exception e) {
            System.err.println("getActors (contacting lookupactor): " + e.getMessage());
            throw e;
        }

        return allActors;
    }

    private ActorRef getActorByName(String name) throws Exception {
        ActorRef actor;
        try {
            final ActorSelection as = akkaSystem.actorSelection(address + "/creatorActor/" + name);
            final Future<ActorRef> actorRefFuture = as.resolveOne(timeout);
            actor = Await.result(actorRefFuture, threeSeconds);
        } catch (Exception e) {
            System.err.println("getActorByName:" + e.getMessage());
            throw e;
        }
        return actor;
    }

    private ActorRef getCreatorActor() throws Exception {
        ActorRef actor;
        try {
            final ActorSelection as = akkaSystem.actorSelection(address + "/user/creatorActor");
            final Future<ActorRef> actorRefFuture = as.resolveOne(timeout);
            actor = Await.result(actorRefFuture, threeSeconds);
        } catch (Exception e) {
            System.err.println("getCreatorActor:" + e.getMessage());
            throw e;
        }
        return actor;
    }

    public Result createActor() {
        String name = "";
        try {
            Form<ActorName> actorNameForm = Form.form(ActorName.class);
            ActorName actorName = actorNameForm.bindFromRequest().get();
            ActorRef creatorActor = getCreatorActor();
            name = actorName.getName();
            if (name.equals("") || name == null) {
                name = "actor0";
            }
            final Inbox inbox = Inbox.create(akkaSystem);
            inbox.send(creatorActor, new CreatorActor.CreateActor(name));
            Object receive =  inbox.receive(threeSeconds);
            if(receive instanceof CreatorActor.ActorCreated){
                ActorRef actorRef = ((CreatorActor.ActorCreated) receive).getActorRef();
                return ok((Content) index.render(" acteur créé ["+actorRef.path().name()+"] full reference = " + actorRef));
            }else{
                if(receive instanceof CreatorActor.ActorCreationException){
                    InvalidActorNameException e = ((CreatorActor.ActorCreationException) receive).getE();
                    System.err.println("create actor: " + e.message());
                    return ok((Content) index.render("create actor: " + e.message()));
                }
            }

        } catch (Exception e) {
            System.err.println("create actor: " + e.getMessage());
            return ok((Content) index.render("create actor: " + e.getMessage()));
        }
        return ok((Content) index.render(" demande de création envoyée pour  " + name));
    }

    public Result askAwait() {

        String id = "vide";
        try {
            ActorRef myActor = getActor0();
            Future<Object> theResponse = ask(myActor, new MyActor.WhatIsYourId(), timeout);
            MyActor.MyId messageMyId = (MyActor.MyId) Await.result(theResponse, timeout.duration());
            id = messageMyId.getId();
        } catch (Exception e) {
            System.err.println("ask await: " + e.getMessage());
            return ok((Content) index.render("ask await: " + e.getMessage()));
        }

        return ok((Content) index.render("ask await: " + id));

    }

    public Promise<Result> askFutures() throws Exception {

        Promise<Result> promisedResult = null;
        ActorRef myActor = getActor0();

        Function<Object, String> functionResponseToId = response -> {
            if (response instanceof MyActor.MyId) {
                MyActor.MyId message = (MyActor.MyId) response;
                return message.getId();
            }
            return "Message is of unexpected type";
        };

        Function<String, Result> functionIdToResult = s -> ok((Content) index.render("ask futures: " + s));

        Future<Object> theResponse = ask(myActor, new MyActor.WhatIsYourId(), timeout);
        final Promise<Object> thePromisedResponse = Promise.wrap(theResponse);
        final Promise<String> thePromisedString = thePromisedResponse.map(functionResponseToId);
        promisedResult = thePromisedString.map(functionIdToResult);

        return promisedResult;
    }


    public Result inboxSend() {

        String id = "vide";
        ActorRef myActor = null;
        try {
            myActor = getActor0();

            final Inbox inbox = Inbox.create(akkaSystem);
            inbox.send(myActor, new MyActor.WhatIsYourId());
            MyActor.MyId receive = (MyActor.MyId) inbox.receive(threeSeconds);
            id = receive.getId();


        } catch (Exception e) {
            System.err.println("inbox send: " + e.getMessage());
            return ok((Content) index.render("inbox send: " + e.getMessage()));
        }
        return ok((Content) index.render("inbox send: " + id));
    }

    public Result multipleAskAwait() {
        ArrayList<String> ids = new ArrayList<String>();
        ArrayList<Future<Object>> theResponses = new ArrayList<Future<Object>>();
        try {
            ArrayList<ActorRef> myActors = getActors();


            for (ActorRef myActor : myActors) {
                theResponses.add(ask(myActor, new MyActor.WhatIsYourId(), timeout));
            }
        } catch (Exception e) {
            System.err.println("multiple ask await (ASK PART): " + e.getMessage());
            return ok((Content)index.render("multiple ask await (ASK PART): " + e.getMessage()));
        }
        try {
            for (Future<Object> theResponse : theResponses) {

                MyActor.MyId messageMyId = (MyActor.MyId) Await.result(theResponse, timeout.duration());
                ids.add(messageMyId.getId());

            }
        } catch (Exception e) {
            System.err.println("multiple ask await (RESPONSE PART): " + e.getMessage());
            return ok((Content)index.render("multiple ask await (RESPONSE PART): " + e.getMessage()));
        }
        return ok(multiple.render(ids));
    }

    public Promise<Result> multipleAskFutures() throws Exception {

        ArrayList<ActorRef> myActors = getActors();
        ArrayList<Future<Object>> theResponses = new ArrayList<Future<Object>>();
        final ExecutionContext ec = akkaSystem.dispatcher();

        for (ActorRef myActor : myActors) {
            theResponses.add(ask(myActor, new MyActor.WhatIsYourId(), timeout));
        }
        final Future<Iterable<Object>> sequence = Futures.sequence(theResponses, ec);

        Future<Result> futureResult = sequence.map(
                new Mapper<Iterable<Object>, Result>() {
                    public Result apply(Iterable<Object> responses) {
                        ArrayList<String> l = new ArrayList<String>();
                        for (Object response : responses) {
                            MyActor.MyId messageMyId = (MyActor.MyId) response;
                            l.add(messageMyId.getId());
                        }
                        return ok(multiple.render(l));
                    }
                }, ec);

        return Promise.wrap(futureResult);

    }


    public Result multipleInboxSend() {
        ArrayList<String> ids = new ArrayList<String>();
        ArrayList<ActorRef> allActors;
        final Inbox inbox = Inbox.create(akkaSystem);

        try {
            allActors = getActors();
            for (ActorRef ar : allActors) {
                inbox.send(ar, new MyActor.WhatIsYourId());
            }
        } catch (Exception e) {
            System.err.println("multiple inbox send (SEND PART): " + e.getMessage());
            return ok((Content)index.render("multiple inbox send (SEND PART): " + e.getMessage()));
        }
        try {
            for (ActorRef myActor : allActors) {
                final MyActor.MyId messageMyId = (MyActor.MyId) inbox.receive(threeSeconds);
                ids.add(messageMyId.getId());
            }
        } catch (Exception e) {
            System.err.println("multiple inbox send (RECEIVE PART): " + e.getMessage());
            return ok((Content)index.render("multiple inbox send (RECEIVE PART): " + e.getMessage()));
        }
        return ok(multiple.render(ids));
    }


}

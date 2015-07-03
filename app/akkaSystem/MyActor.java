package akkaSystem;

import akka.actor.UntypedActor;

import java.io.Serializable;

public class MyActor extends UntypedActor {
  @Override
  public void onReceive(Object message) {

    if (message instanceof WhatIsYourId) {
        getSender().tell(new MyId(getSelf().path().name()+" ID = "+getSelf().path().uid()),getSelf());
    } else {
      unhandled(message);
    }
  }

    public static class WhatIsYourId implements Serializable {
        public WhatIsYourId(){
        }
    }

    public static class MyId implements Serializable{
        private String id = "no id";
        public MyId(String id){
            this.id=id;
        }
        public String getId(){return id;}
    }
}

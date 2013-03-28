//
// Copyright 2013 Vibul Imtarnasan, David Bolton and Socko contributors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package org.mashupbots.socko.rest.get

import org.mashupbots.socko.rest.RestGet
import org.mashupbots.socko.rest.RestRequest
import org.mashupbots.socko.rest.RestRequestContext
import org.mashupbots.socko.rest.RestResponse
import org.mashupbots.socko.rest.RestResponseContext
import akka.actor.Actor
import org.mashupbots.socko.rest.RestPath
import org.mashupbots.socko.events.HttpResponseStatus
import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Props
import org.mashupbots.socko.rest.RestDispatcher

@RestGet(urlTemplate = "/object/{status}")
case class GetObjectRequest(context: RestRequestContext, @RestPath() status: Int) extends RestRequest

case class Pet(name: String, age: Int)
case class GetObjectResponse(context: RestResponseContext, pet: Option[Pet]) extends RestResponse

class GetObjectProcessor() extends Actor with akka.actor.ActorLogging {
  def receive = {
    case req: GetObjectRequest =>
      if (req.status == 200) {
        sender ! GetObjectResponse(
          req.context.responseContext(HttpResponseStatus(req.status)),
          Some(Pet("Boo", 5)))
      } else {
        sender ! GetObjectResponse(
          req.context.responseContext(HttpResponseStatus(req.status)),
          None)
      }
      context.stop(self)
  }
}

class GetObjectDispatcher extends RestDispatcher {
  def getActor(actorSystem: ActorSystem, request: RestRequest): ActorRef = {
    actorSystem.actorOf(Props[GetObjectProcessor])
  }
}
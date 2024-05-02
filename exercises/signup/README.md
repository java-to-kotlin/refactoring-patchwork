# Conference Session Signup refactoring exercise

_From mutable beans, to immutable values, to unrepresentable illegal states_

## Scenario

The code we are working on implements sign-up for conference sessions.

```plantuml
:Attendee: as a
:Presenter: as p
:Admin: as b

component "Attendee's Phone" as aPhone {
    component [Conference App] as aApp
}
component "Presenter's Phone" as pPhone {
    component [Conference App] as pApp
}
component "Web Browser" as bBrowser
component "Conference Web Service" as webService

a -right-> aApp : "sign-up\ncancel sign-up"
aApp -down-> webService : HTTP

p -left-> pApp : "close signup\nlist attendees"
pApp -down-> webService : HTTP

b -right-> bBrowser : "add sessions\nlist attendees"
bBrowser -right-> webService : HTTP
```

An admin user creates sign-up sheets for sessions in an admin app (not covered in this example). 
Sessions have limited capacity, set when the sign-up sheet is created.
Attendees sign up for sessions via mobile conference app.  
Admins can also sign attendees up for sessions via the admin app.
The session presenter starts the session via the mobile conference app.  After that, the sign-up sheet cannot be changed.

## Simplifications

The code is simplified for the sake of brevity and clarity:

* It doesn't cover some edge cases.  The techniques we will use in the exercise apply equally well to those too.
* It doesn't include authentication, authorisation, monitoring, tracing, etc., to focus on the topic of the exercise.


## Review the Kotlin code in the conf.signup.server package

Classes:

* `SignupSheet` manages the sign-up for a single conference session  

* A `SignupBook` is a collection of signup sheets for all the sessions in the conference 
  * Hexagonal architecture: the `SignupBook` interface is defined in the application domain model and hides the choice of technical persistence layer
  * Sheets are added to the signup book out-of-band by an admin app, which is not shown in the example code.

* `SignupApp` implements the HTTP API by which a front-end controls the SignupSheet.
  * Routes on request path and method
  * Supports attendee sign up and cancellation, closing signup to a session, and listing who is signed up.
  * Translates exceptions from the SignupSheet into HTTP error responses

* SessionSignupAppTests: behaviour is tested at the HTTP API, with fast, in-memory storage.  
  * The InMemorySignupBook and InMemoryTransactor emulate the semantics of the persistence layer

* SignupServer:
  * Example of a real HTTP server storing signups in memory.  You can run this and play with the server using IntelliJ's HTTP requests plugin.


NOTE: The name of the SignupApp function follows Http4k conventions. An "App" includes the HTTP routing and business logic. A "Server" runs the App in an HTTP server, with an HTTP "Stack" that provides monitoring, authentication flows, etc.


## Getting started

Run the tests to confirm that they pass.

Use "F2" to navigate through the IntelliJ warnings.  Automatically address issues in SignupApp.kt with Alt-Enter.

Now... Let's refactor this to *idiomatic* Kotlin.


## Refactoring task

* Address code smells
* Refactor to a _functional_ domain model
* Introduce type safety

Our strategy is to start by converting the domain model and work outwards towards the HTTP layer.


### What code smells?

The class is a Kotlin implementation of Java style "bean" code.

* Essential behaviour implemented by mutation (signups)
* Also _inappropriate_ mutation (e.g. sessionId, capacity)
* Throws exceptions if client code uses the object incorrectly.
* Exceptions are caught at the HTTP layer and translated to HTTP status codes

Wouldn't it be better if the client code could NOT use the object incorrectly?

We can make that happen by refactoring to represent different states at the type level.

However, we cannot do that while the code uses mutable state... Kotlin cannot represent _dynamic_ aspects of mutable state in its _static_ type system. To introduce type safety, we must remove mutation, so let's do that first.

### Reviewing the SignupApp

The SignupSheet is used in the SignupApp. If we are going to make the SignupSheet immutable, we'll need to change this HTTP handler to work with immutable values, rather than mutable beans.

SignupApp It is a typical Http4k handler... although Http4k implements "server as a function", the handler is not a pure function: it reads and writes to the SignupSheet, which is our abstraction for a database.


## Converting the bean to an immutable data class

### Replacing mutability with immutability

First make `SignupSheet` immutable, and later use the type system to make it impossible for client code to call methods when the object is in an inappropriate state.

A general strategy for converting classes from mutable to immutable is to push mutation from the inside outwards.  E.g. converting a val that holds a mutable object into a var that holds an immutable object.  And continuing this strategy to push mutation into external resources (databases, for example).

We'll start by applying this strategy "in the small", by making the `signups` set immutable. We will replace the immutable reference to a MutableSet with a _mutable_ reference to an immutable Set, and make the setter private:

* Find usages of the `signups` property.  There are several references in this class that mutate the Set by calling its methods.  All these methods are operator methods, and so the method calls can be replaced by operators that Kotlin desugars to mutation of a mutable set or to transformation of an immutable set held in a mutable variable. Using the operators will let us push mutation outwards without breaking the app. You can use Alt-Enter in IntelliJ to refactor between method call and operator.
  * Replace the call to the add method with `signups = signups + attendeeId`
  * Replace the call to the remove method with `signups = signups - attendeeId`. (For some reason, IntelliJ does not have offer this when you hit Alt-Enter. You have to do this by a manual edit.)
  * For consistency, you can also replace the call to the contains method with the `in` operator, but because that is a query it has no bearing on the mutability of the class.
  * Run the tests to make sure everything still works

* Change the declaration of `signups` to: `var signups = setOf<AttendeeId>()`.

Run the tests. They pass. COMMIT!

**Review**: We pushed mutation one level outwards. We did so without breaking the application by making the application use an API that is the same whether mutating a mutable object held in a `val` or transforming an immutable object held in a `var`.


### Now for the bean itself

We'll now apply the same strategy to how the SignupApp uses the SignupSheet.

However, we have a chicken-and-egg situation... SignupSheet needs functional operations before we can use the strategy, and we need to have applied the strategy to make SignupSheet functional.  The change feels too big to do in one go.

We need _another_ strategy to introduce those functional operations without breaking the application:

1. Change the SignupSheet so that its API looks functional but also mutates the object – a so-called "fluent" or "chained" API style.
2. Change clients to use the chained API so that they treat the SignupSheet as if it were immutable
3. Make the SignupSheet immutable

#### Step 1: make the mutator methods return `this`

* Add `return this` at the end of `close`, and use Alt-Enter to add the return type to the method signature
* Do the same for the `signUp` and `cancelSignUp` methods

Run the tests. They pass. COMMIT!

#### Step 2: Invoke mutator methods as if functions

In SignupApp, replace sequential statements that mutate and then save with a single statement passes the result of the mutator to the `save` method, like:

~~~
book.save(sheet.close())
~~~

We can make IntelliJ do this automatically by extracting the call to the chainable mutator into a local variable called `sheet`.  IntelliJ will warn about shadowing.  That's ok. Now inline the local `sheet` variable, and the call to the chainable mutator will be inlined as a parameter of `book.save`.

Run the tests. They pass. COMMIT!

#### Step 3: Turn the mutator methods of `SignupSheet` into transformations

In SignupServer, replace the mutation of the sheet with a call to the secondary constructor and inline the `sheet` variable.

We don't have a test for the server -- it is test code -- but COMMIT! anyway.  The use of domain-specific value types means that the type checker gives us good confidence that this refactor is correct.

We can now delete the no-arg constructor.  There's no easy way to do this automatically in IntelliJ.  You'll have to do so with a manual edit: delete the `()` after the class name, and the call to `this()` in the secondary constructor declaration.

* ASIDE: Like a lot of real-world Java code, this example uses Java Bean naming conventions but not actual Java Beans.

Run the tests. They pass. COMMIT!

Convert the secondary constructor to a primary constructor by clicking on the declaration and Option-Enter.

Run the tests. They pass. COMMIT!

Make sessionId a non-nullable val.

Make capacity a val. Delete the entire var property including the checks. Those are now enforced by the type system.  IntelliJ highlights the declaration in the class body as redundant.  Use Option-Enter to move the val declaration to the primary constructor.

Run the tests. They pass. COMMIT!

* Move the declaration of `signups` to primary constructor, initialised to `emptySet()`
* Declare `isClosed` as a val in the primary constructor, initialised as `false`
* Try running the tests...  The mutators do not compile.  Change them so that, instead of mutating a property, they return a new copy of the object that the property changed.  It's easiest to declare the class as a `data class` and call the `copy` method.

Run the tests... they fail!  We also have to update our in-memory simulation of persistence, the InMemorySignupBook.

* All the code to return a copy of the stored SignupSheet is now unnecessary because SignupSheet is immutable. Delete it all, and return the value obtained from the map

Run the tests. They pass. COMMIT!

### Tidying up

We can turn some more methods into expression form.

* We cannot do this for signUp and cancelSignup because of those checks.  We'll come back to those shortly...

ASIDE: I prefer to use block form for functions with side effects and expression for pure functions.

Run the tests. They pass. COMMIT!


The data class does allow us to make the state of a signup sheet inconsistent, by passing in more signups than the capacity.

* Add a check in the init block:

    ~~~
    init {
        check(signups.size <= capacity) {
            "cannot have more sign-ups than capacity"
        }
    }
    ~~~
  
    If you have a reference to a SignupSheet, it's guaranteed to have consistent state. 

* This makes the `isFull` check in `signUp` redundant, so delete it.


## Making illegal states unrepresentable

Now... those checks... it would be better to prevent client code from using the SignupSheet incorrectly than to throw an exception after they have used it incorrectly.  In FP circles this is sometimes referred to as "making illegal states unrepresentable".

The SignupSheet class implements a state machine:


~~~plantuml

state Open {
    state choice <<choice>>
    state closed <<exitPoint>>
    state open <<entryPoint>>
    
    open -down-> Available
    Available -down-> Available : cancelSignUp(a)
    Available -right-> choice : signUp(a)
    choice -right-> Full : [#signups = capacity]
    choice -up-> Available : [#signups < capacity]
    Full -left-> Available : cancelSignUp(a)
    
    Available -> closed : close()
    Full -> closed : close()
}

[*] -down-> open
closed -> Closed
~~~

* The _signUp_ operation only makes sense in the Available sub-state of Open.

* The _cancelSignUp_ operation only makes sense in the Open state.

* The _close_ operation only makes sense in the Open state.

We can express this in Kotlin with a _sealed type hierarchy_...

~~~plantuml
hide empty members
hide circle

class SignupSheet <<sealed>>
class Open <<sealed>> extends SignupSheet {
    close(): Closed
    cancelSignUp(a): Available
}

class Available extends Open {
    signUp(a): Open
}

class Full extends Open

class Closed extends SignupSheet
~~~


We'll introduce this state by state, starting with Open vs Started, replacing predicates of the properties of the class with subtype relationships.

Unfortunately, IntelliJ doesn't have any automated refactorings to split a class into a sealed hierarchy, so we'll have to do it the old-fashioned way... by hand ... like C++ programmers...

### Open/Closed states

* Extract a sealed base class from SignupSheet
  * NOTE: IntelliJ seems to have lost the ability to rename a class and extract an interface with the original name.  So, we'll have to extract the base class with a temporary name and then rename class and interface to what we want.
  * call it anything, we're about to rename it.  SignupSheetBase, for example.
  * Pull up sessionId, capacity & signups as abstract members.
  * Pull up isSignedUp and isFull as a concrete members.
  * This refactoring doesn't work 100% for Kotlin, so fix the errors in the interface by hand.

* Change the name of the subclass by hand (not a rename refactor) to Open, and then use a rename refactoring to rename the base class to SignupSheet.
* Repeatedly run all the tests to locate all the compilation errors...
  * In SignupApp, there are calls to methods of the Open class that are not defined on the SignupSheet class.
    * wrap the logic for each HTTP method in `when(sheet) { is Open -> ... }` to get things compiling again. E.g.

      ~~~
      when (sheet) {
          is Open ->
              try {
                  book.save(sheet.signUp(attendeeId))
                  sendResponse(exchange, OK, "subscribed")
              } catch (e: IllegalStateException) {
                  sendResponse(exchange, CONFLICT, e.message)
              }
          }
      }
      ~~~

    * In SessionSignupHttpTests and SignupServer we need to create Open instead of SessionSignup. IntelliJ doesn't yet have a "Replace constructor with factory method" refactoring for Kotlin classes.  But we can do the same thing manually... define a function to create new signup sheets in SignupSheet.kt, called `SignupSheet:
    
      ~~~
      fun SignupSheet(sessionId: SessionId, capacity: Int) =
          Open(sessionId, capacity)
      ~~~

Run the tests. They pass. COMMIT!

Now we can add the Closed subclass:

* NOTE: do not use the "Implement sealed class" action... it does not give the option to create the class in the same file. Instead...
* Define a new `data class Closed : SignupSheet()` in the same file
* The new class is highlighted with an error underline. Option-Enter on the highlighted error, choose "Implement as constructor parameters", ensure sessionId, capacity, and signups are selected in the pop-up (default behaviour), and perform the action.

We've broken the SignupApp, so before we use the Closed class to implement our state machine, let's get it compiling again.

* Add when clauses for Closed that just call TODO(), by Option-Enter-ing on the errors and selecting "Add remaining branches"

Run the tests to verify that we have not broken anything... we are not actually using the Closed class yet.

Now make Open.close() return an instance of Closed:

~~~
fun close() =
    Closed(sessionId, capacity, signups)
~~~

Run the tests: there are failures because of the TODO() calls:

* for `signupPath`, replace TODO calls by returning a CONFLICT status with an error message as the body text: `Response(CONFLICT).body("session closed")`
* for `closedPath`:
  * GET: replace the entire when clause with `Response(OK).body(sheet is Closed)`
  * POST: there is nothing to do if signup is already closed, replace the TODO() with `Response(OK).body("closed")`

Run the tests. They pass. COMMIT!

Look for uses of isClosed. The only calls are accessors in the checks.  Therefore, the value never changes, and is always false.  The checks are dead code, because we have replaced the use of the boolean property with subtyping.

* Delete the check statements
* Safe-Delete the isClosed constructor parameter

Run the tests. They pass. COMMIT!

Review the class... More of the methods are now pure functions. We can turn them into single-expression form. They are declared to return the abstract SessionSignup type. We can make the code express the state transitions explicitly in the type system be declaring the methods to return the concrete type (or letting Kotlin infer the result type).

* ASIDE: I prefer to explicitly declare the result type I want.
* Declare the result of close() as Closed, and of signUp & cancelSignUp as Open

Run the tests. They pass. COMMIT!

### Available/Full states

We still have the try/catch blocks because the SignupSheet throws IllegalStateException if you call sign up when the session is full.  We can represent that with types in the same way...  We will create subclasses of Open: `Available`, to represent a session that has signups available, and `Full` to represent a full session that users cannot sign up to. 

Rename Open to Available

Run all the tests.  They should still pass.

Change the implementation of `cancelSignup` to return an instance of Available, rather than calling `copy`, and pull it up into the Open superclass as a concrete method. Cancelling a signup always creates some Availability, whether the session is Full or not.

Extract an abstract superclass Open, pulling up close as concrete. (Ignore the members highlighted in red in the dialog - they will be inherited from the SignupSheet base class).

Make Open a sealed class.  This will get rid of any compilation errors.

Run all the tests.  They should still pass.

Add a new subclass, Full, derived from Open, like this:

~~~
data class Full : Open()
~~~

* It will be underlined with a red error highlight.
* Option-Enter on the error, select "Implement as constructor parameters", and select sessionId and signups in the dialog
* The class will still be underlined with a red error highlight because `capacity` has not been implemented yet
* Option-Enter on the error, select "Implement members", and select Ok
* Implement `capacity` to evaluate to `signups.size`
* The end result should therefore be:

    ~~~
    data class Full(
        override val sessionId: SessionId,
        override val signups: Set<AttendeeId>
    ) : Open() {
        override val capacity: Int
            get() = signups.size
    }
    ~~~

Run all the tests.  Now SignupApp won't compile because the Full case is not handled.

Make all the `when` expressions exhaustive:

* in handleSignup for POST, Option-Enter on the `when` and choose "Add remaining branches" to insert a TODO() temporarily
* in handleSignup for DELETE and handleStarted, change when condition from `is Available` to `is Open`


Change Available::signUp to return Available or Full, depending on whether the number of signups reaches capacity:

* Change the return type to Open
* extract `signups + attendeeId` as a variable, newSignups
* Change result to return Full when newSignups.size == capacity:

    ~~~
    return when (newSignups.size) {
        capacity -> Full(sessionId, newSignups)
        else -> copy(signups = newSignups)
    }
    ~~~

Run the tests.  They fail.

Make them pass by:

* Implementing the `is Full` condition as:

  ~~~
  is Full -> {
      sendResponse(exchange, CONFLICT, "session full")
  }
  ~~~

Run the tests. They pass. COMMIT!

We can remove the isFull property.  It is only now only true for the `Full` type.
 * Find uses of isFull: there is only one use, in the SignupApp.
 * Replace the property access `sheet.isFull` with `sheet is Full`   
 * Safe-delete the isFull property

Run all the tests.  They pass.  COMMIT!

Review the subclasses of SignupSheet.  The classes no longer check that methods are called in the right state. The only remaining check, in the init block, defines a class invariant that the internal implementation maintains. We can remove the try/catch in our HTTP handler!  We don't strictly need the init block any more, Remove that as well.

* Unwrap the try/catch blocks in the SignupApp (add braces to when clause with Option-Enter if necessary)


## Converting the methods to extensions

If we have time, convert methods to extensions (Option-Enter on the methods).

Change the result types to the most specific possible.

Gather the types and functions into two separate groups.

Fold away the function bodies. Ta-da!  The function signatures describe the state machine!


## Wrap up

Review the code of SignupSheet and SignupApp

What have we done?

* Refactored a mutable, java-bean-style domain model to an immutable, algebraic data type and operations on the data type.
  * Pushed mutation outward, to the edge of our system
* Replaced runtime tests throwing exceptions for invalid method calls, with type safety: it is impossible to call methods in the wrong state because those operations do not exist in those states
  * Pushed error checking outwards, to the edge of the system, where the system has the most context to handle or report the error

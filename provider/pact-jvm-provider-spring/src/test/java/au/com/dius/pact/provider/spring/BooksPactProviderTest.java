package au.com.dius.pact.provider.spring;

import au.com.dius.pact.provider.junit.Provider;
import au.com.dius.pact.provider.junit.RestPactRunner;
import au.com.dius.pact.provider.junit.State;
import au.com.dius.pact.provider.junit.loader.PactFolder;
import au.com.dius.pact.provider.junit.target.TestTarget;
import au.com.dius.pact.provider.spring.target.MockMvcTarget;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

@RunWith(RestPactRunner.class)
@Provider("Books-Service")
@PactFolder("pacts")
public class BooksPactProviderTest {

    //Mock your service (logic) class.  We'll use this to create scenarios for respective provider states.
    @Mock
    private BookLogic bookLogic;

    //Create instance(s) of your controller(s).  We cannot autowire controllers as we're not using (and don't want to use) a Spring test runner.
    @InjectMocks
    private BookController bookController = new BookController();

    @InjectMocks
    private NovelController novelController = new NovelController();

    //Create instance(s) of your exception handler(s) to be passed to the MockMvcTarget constructor and wired up with MockMvc.
    @InjectMocks
    private BookControllerAdviceOne bookControllerAdviceOne = new BookControllerAdviceOne();

    @InjectMocks
    private BookControllerAdviceTwo bookControllerAdviceTwo = new BookControllerAdviceTwo();

    //Create the MockMvcTarget with your controller and exception handler.  The third parameter, when set to true, will
    //print verbose request/response information for all interactions with MockMvc.
    @TestTarget
    public final MockMvcTarget target = new MockMvcTarget();

    private final DateTime DATE_TIME = DateTime.now(DateTimeZone.UTC).withTimeAtStartOfDay();

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        target.setControllers(bookController, novelController);
        target.setControllerAdvice(bookControllerAdviceOne, bookControllerAdviceTwo);
        target.setServletPath("/api");

        target.setMessageConvertors(
            new MappingJackson2HttpMessageConverter(
                new ObjectMapper()
                    .registerModule(new JodaModule())
                    .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            )
        );
    }

    @State("book-exists")
    public void bookFound() {
        when(bookLogic.getBookById(any(UUID.class)))
                .thenReturn(new Book(UUID.randomUUID(), "Nick Hoftsettler", true, DATE_TIME));
    }

    @State("book-not-found")
    public void bookNotFound() {
        when(bookLogic.getBookById(any(UUID.class)))
                .then(i -> { throw new BookNotFoundException((UUID) i.getArguments()[0]); });
    }

    @State("create-book")
    public void createBook() {
        // no setup needed
    }

    @State("create-book-bad-data")
    public void createBookBadData() {
        when(bookLogic.createBook(any(Book.class)))
                .then(i -> { throw new BookValidationException((Book) i.getArguments()[0]); });
    }

    @State("update-book")
    public void updateBook() {
        // no setup needed
    }

    @State("delete-book")
    public void deleteBook() {
        // no setup needed
    }

    @State("update-book-no-content-type")
    public void updateBookNoContentType() {
        // no setup needed
    }

    @State("get-books-by-type")
    public void getBooksByType() {
        // Prove that we can provide MockMvcTarget with our own pre-build MockMvc for situations where we need greater control over
        // how MockMvc is configured; in this instance the request needs a custom argum
        target.setMockMvc(MockMvcBuilders.standaloneSetup(bookController)
          .setCustomArgumentResolvers(new BookTypeArgumentResolver())
          .defaultRequest(MockMvcRequestBuilders.get("/").servletPath("/api"))
          .build());

        List<Book> bookList = new ArrayList<>();
        bookList.add(new Book(UUID.randomUUID(), "Bob Jones", true, DATE_TIME));
        bookList.add(new Book(UUID.randomUUID(), "Eric Reynolds", true, DATE_TIME.plusDays(1)));

        when(bookLogic.getBooks(any(BookType.class))).thenReturn(bookList);
    }

    @State("get-books")
    public void getAllBooks() {

        List<Book> bookList = new ArrayList<Book>();

        bookList.add(new Book(UUID.randomUUID(), "Bob Jones", true, DATE_TIME));
        bookList.add(new Book(UUID.randomUUID(), "Jerry Duff", false, DATE_TIME.plusDays(1)));
        bookList.add(new Book(UUID.randomUUID(), "Eric Reynolds", true, DATE_TIME.plusDays(2)));

        when(bookLogic.getBooks())
                .thenReturn(bookList);
    }

    @State("get-best-selling-books")
    public void getBestSellingBooks() {

        List<Book> bookList = new ArrayList<Book>();

        bookList.add(new Book(UUID.randomUUID(), "Bob Jones", true, DATE_TIME));
        bookList.add(new Book(UUID.randomUUID(), "Eric Reynolds", true, DATE_TIME.plusDays(1)));

        when(bookLogic.getBooks(true))
                .thenReturn(bookList);
    }

    @State("novel-exists")
    public void novelFound() {
        when(bookLogic.getBookById(any(UUID.class)))
                .thenReturn(new Book(UUID.randomUUID(), "Nick Hoftsettler", true, DATE_TIME));
    }
}
